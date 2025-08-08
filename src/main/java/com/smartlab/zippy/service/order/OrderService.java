package com.smartlab.zippy.service.order;

import com.smartlab.zippy.model.dto.web.request.order.OrderRequest;
import com.smartlab.zippy.model.dto.web.response.order.OrderResponse;
import com.smartlab.zippy.model.dto.robot.RobotContainerStatusDTO;
import com.smartlab.zippy.model.dto.robot.RobotStatusDTO;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Product;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.model.entity.User;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.ProductRepository;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.repository.UserRepository;
import com.smartlab.zippy.service.robot.RobotDataService;
import com.smartlab.zippy.service.trip.TripCodeGenerator;
import com.smartlab.zippy.service.trip.TripStatusService;
import com.smartlab.zippy.service.qr.QRCodeService;
import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.web.response.qr.QRCodeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TripRepository tripRepository;
    private final RobotRepository robotRepository;
    private final UserRepository userRepository;
    private final RobotDataService robotDataService;
    private final TripCodeGenerator tripCodeGenerator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final QRCodeService qrCodeService;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final TripStatusService tripStatusService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for user: {}, product: {}, robot: {}",
                request.getUsername(), request.getProductName(), request.getRobotCode());

        // Find user by username to get userId
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found with username: " + request.getUsername()));

        // Validate robot is online and free using cached data
        validateRobotAvailability(request.getRobotCode(), request.getRobotContainerCode());

        // Create or find active trip for the robot (using robotCode to get robotId from cache)
        Trip trip = findOrCreateActiveTrip(request.getRobotCode(), request.getEndpoint(), user.getId());

        // Create new product with auto-generated ID
        Product product = Product.builder()
                .code(request.getProductName()) // Use product name as code
                .tripId(trip.getId())
                .containerCode(request.getRobotContainerCode())
                .build();

        Product savedProduct = productRepository.save(product);

        // Generate unique order code
        String orderCode = orderCodeGenerator.generateOrderCode();

        // Create order with userId from the found user and the new product ID
        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(user.getId())
                .tripId(trip.getId())
                .productId(savedProduct.getId()) // Use the auto-generated product ID
                .price(BigDecimal.ZERO) // Default price, can be calculated based on business logic
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // Build response
        return OrderResponse.builder()
                .orderId(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .productName(savedProduct.getCode()) // Use product code from saved product
                .robotCode(request.getRobotCode())
                .robotContainerCode(request.getRobotContainerCode())
                .endpoint(request.getEndpoint())
                .price(savedOrder.getPrice())
                .status(savedOrder.getStatus())
                .createdAt(savedOrder.getCreatedAt())
                .completedAt(savedOrder.getCompletedAt())
                .build();
    }

    private void validateRobotAvailability(String robotCode, String robotContainerCode) {
        // Check if robot is online using cached data
        if (!robotDataService.isRobotOnline(robotCode)) {
            throw new RuntimeException("Robot " + robotCode + " is not online");
        }

        // Check robot status using cached data
        Optional<RobotStatusDTO> robotStatusOpt = robotDataService.getStatus(robotCode);
        if (robotStatusOpt.isEmpty()) {
            throw new RuntimeException("Robot " + robotCode + " status not available");
        }

        RobotStatusDTO robotStatus = robotStatusOpt.get();
        if (!"free".equalsIgnoreCase(robotStatus.getStatus())) {
            throw new RuntimeException("Robot " + robotCode + " is not available (status: " + robotStatus.getStatus() + ")");
        }

        // Check container status using cached data
        Optional<RobotContainerStatusDTO> containerStatusOpt =
                robotDataService.getContainerStatus(robotCode, robotContainerCode);
        if (containerStatusOpt.isEmpty()) {
            throw new RuntimeException("Container " + robotContainerCode + " status not available for robot " + robotCode);
        }

        RobotContainerStatusDTO containerStatus = containerStatusOpt.get();
        if (!"free".equalsIgnoreCase(containerStatus.getStatus())) {
            throw new RuntimeException("Container " + robotContainerCode + " is not available (status: " + containerStatus.getStatus() + ")");
        }

        log.info("Robot {} and container {} are available for order", robotCode, robotContainerCode);
    }

    private Trip findOrCreateActiveTrip(String robotCode, String endpoint, UUID userId) {
        // Look for an active trip for the robot code - uses JOIN query to avoid lazy loading issues
        Optional<Trip> activeTrip = tripRepository.findActiveByRobotCode(robotCode);

        if (activeTrip.isPresent()) {
            Trip existingTrip = activeTrip.get();
            // Update trip with endpoint and user ID if not already set
            if (existingTrip.getEndPoint() == null || existingTrip.getUserId() == null) {
                existingTrip.setEndPoint(endpoint);
                existingTrip.setUserId(userId);
                return tripRepository.save(existingTrip);
            }
            return existingTrip;
        }

        // For new trip creation, we need the actual robot UUID from database
        // This is a minimal database query just to get the robot UUID for trip creation
        UUID robotId = getRobotIdByCode(robotCode);

        // Generate unique trip code
        String tripCode = tripCodeGenerator.generateTripCode();

        // Create new trip if no active trip exists - starts with PENDING status
        Trip newTrip = Trip.builder()
                .tripCode(tripCode)
                .robotId(robotId)
                .userId(userId)
                .endPoint(endpoint)
                .status("PENDING") // Trip starts as PENDING, becomes ACTIVE when robot receives move command
                .startTime(LocalDateTime.now())
                .build();

        return tripRepository.save(newTrip);
    }

    private UUID getRobotIdByCode(String robotCode) {
        // Minimal database query to get robot UUID for trip creation only
        // The availability checking was already done using cached data
        Robot robot = robotRepository.findByCode(robotCode)
                .orElseThrow(() -> new RuntimeException("Robot not found with code: " + robotCode));
        return robot.getId();
    }

    public List<OrderResponse> getAllOrders() {
        log.info("Retrieving all orders");

        Iterable<Order> orderIterable = orderRepository.findAll();
        List<Order> orders = new ArrayList<>();
        orderIterable.forEach(orders::add);

        return orders.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByUsername(String username) {
        log.info("Retrieving orders for username: {}", username);

        List<Order> orders = orderRepository.findByUsername(username);

        return orders.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    private OrderResponse convertToOrderResponse(Order order) {
        // Get product information from product table using product ID
        String productName = "N/A";
        String robotContainerCode = "N/A";
        if (order.getProductId() != null) {
            Optional<Product> product = productRepository.findById(order.getProductId());
            if (product.isPresent()) {
                productName = product.get().getCode(); // Using product code as product name
                robotContainerCode = product.get().getContainerCode();
            }
        }

        // Get robot code and endpoint from trip information
        String robotCode = "N/A";
        String endpoint = "N/A";
        if (order.getTripId() != null) {
            Optional<Trip> trip = tripRepository.findById(order.getTripId());
            if (trip.isPresent()) {
                endpoint = trip.get().getEndPoint();
                // Get robot code from robot table using robot ID
                if (trip.get().getRobotId() != null) {
                    Optional<Robot> robot = robotRepository.findById(trip.get().getRobotId());
                    if (robot.isPresent()) {
                        robotCode = robot.get().getCode();
                    }
                }
            }
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .productName(productName) // Product name from product table
                .robotCode(robotCode) // Robot code from robot table via trip
                .robotContainerCode(robotContainerCode) // Container code from product table
                .endpoint(endpoint) // Endpoint from trip table
                .price(order.getPrice())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .build();
    }

    public QRCodeResponse generateAndSendQRCode(String orderCode) {
        log.info("Generating QR code for orderCode: {}", orderCode);

        // Find the order by order code
        Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with code: " + orderCode);
        }

        Order order = orderOpt.get();

        // Get trip information to find robot code
        String robotCode = "N/A";
        String tripCode = "N/A";
        if (order.getTripId() != null) {
            Optional<Trip> trip = tripRepository.findById(order.getTripId());
            if (trip.isPresent()) {
                tripCode = trip.get().getTripCode();
                if (trip.get().getRobotId() != null) {
                    Optional<Robot> robot = robotRepository.findById(trip.get().getRobotId());
                    if (robot.isPresent()) {
                        robotCode = robot.get().getCode();
                    }
                }
            }
        }

        // Create QR code data (JSON format with order and trip information)
        String qrData = String.format(
            "{\"orderCode\":\"%s\",\"tripCode\":\"%s\",\"timestamp\":\"%s\"}",
            orderCode, tripCode, LocalDateTime.now()
        );

        // Generate QR code
        String qrCodeBase64 = qrCodeService.generateQRCode(qrData);

        // Send QR code to robot via MQTT
        String mqttTopic = String.format("robot/%s/command/qr", robotCode);
        String mqttPayload = String.format(
            "{\"orderCode\":\"%s\",\"tripCode\":\"%s\",\"qrCode\":\"%s\"}",
            orderCode, tripCode, qrCodeBase64
        );

        try {
            mqttCommandPublisher.publish(mqttPayload, mqttTopic);
            log.info("QR code sent to robot {} via MQTT topic: {}", robotCode, mqttTopic);
        } catch (Exception e) {
            log.error("Failed to send QR code to robot {}: {}", robotCode, e.getMessage(), e);
            throw new RuntimeException("Failed to send QR code to robot", e);
        }

        return QRCodeResponse.builder()
                .orderCode(orderCode)
                .qrCodeBase64(qrCodeBase64)
                .robotCode(robotCode)
                .message("QR code generated and sent to robot successfully")
                .build();
    }

    /**
     * Verify OTP and complete order
     * Sets order status to FINISHED and trip status to COMPLETED
     * @param orderCode Order code to verify
     * @param otp OTP to verify
     */
    @Transactional
    public void verifyOTPAndCompleteOrder(String orderCode, String otp) {
        log.info("Verifying OTP for orderCode: {}", orderCode);

        // Find the order by order code
        Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with code: " + orderCode);
        }

        Order order = orderOpt.get();

        // TODO: Implement actual OTP verification logic here
        // For now, accepting any non-empty OTP as valid
        boolean isOtpValid = otp != null && !otp.trim().isEmpty();

        if (!isOtpValid) {
            throw new RuntimeException("Invalid OTP provided");
        }

        // Update order status to FINISHED and set completion time
        order.setStatus("FINISHED");
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} status set to FINISHED", orderCode);

        // Update trip status to COMPLETED using TripStatusService
        if (order.getTripId() != null) {
            Optional<Trip> tripOpt = tripRepository.findById(order.getTripId());
            if (tripOpt.isPresent()) {
                Trip trip = tripOpt.get();
                String tripCode = trip.getTripCode();
                if (tripCode != null) {
                    tripStatusService.completeTripByOtpVerification(tripCode);
                    log.info("Trip {} status set to COMPLETED via TripStatusService", tripCode);
                } else {
                    log.warn("Trip code is null for trip ID: {}", trip.getId());
                }
            } else {
                log.warn("Trip not found for order: {}", orderCode);
            }
        } else {
            log.warn("No trip associated with order: {}", orderCode);
        }

        log.info("Successfully completed OTP verification for order {} and associated trip", orderCode);
    }


    public boolean approveOrder(String orderCode) {
        log.info("Approving order with orderCode: {}", orderCode);
        if (orderRepository.findByOrderCode(orderCode).isPresent()) {
            // Update order status to APPROVED
            Order order = orderRepository.findByOrderCode(orderCode).get();
            order.setStatus("APPROVED");
            orderRepository.save(order);
            log.info("Order {} approved successfully", orderCode);
            return true;
        } else {
            log.warn("Order with code {} not found for approval", orderCode);
            return false;
        }
    }
}
