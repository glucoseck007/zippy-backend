package com.smartlab.zippy.service.order;

import com.smartlab.zippy.model.dto.web.request.order.BatchOrderRequest;
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
import com.smartlab.zippy.repository.RobotContainerRepository;
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
    private final RobotContainerRepository robotContainerRepository;
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
        log.info("Creating order from sender: {} to receiver: {}, product: {}, robot: {}",
                request.getSenderIdentifier(), request.getReceiverIdentifier(), request.getProductName(), request.getRobotCode());

        // Find sender by email or phone to get userId
        User sender = findUserByIdentifier(request.getSenderIdentifier())
                .orElseThrow(() -> new RuntimeException("Sender not found with identifier: " + request.getSenderIdentifier()));

        // Find receiver by email or phone to get receiverId
        User receiver = findUserByIdentifier(request.getReceiverIdentifier())
                .orElseThrow(() -> new RuntimeException("Receiver not found with identifier: " + request.getReceiverIdentifier()));

        // Validate robot is online and free using cached data
        validateRobotAvailability(request.getRobotCode(), request.getRobotContainerCode());

        // Create or find active trip for the robot (now includes robot container ID)
        Trip trip = findOrCreateActiveTrip(request.getRobotCode(), request.getRobotContainerCode(), request.getStartPoint(), request.getEndpoint(), sender.getId());

        // Create new product with auto-generated ID
        Product product = Product.builder()
                .code(request.getProductName()) // Use product name as code
                .tripId(trip.getId())
                .containerCode(request.getRobotContainerCode())
                .build();

        Product savedProduct = productRepository.save(product);

        // Generate unique order code
        String orderCode = orderCodeGenerator.generateOrderCode();

        // Create order with both sender and receiver IDs
        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(sender.getId()) // Sender ID
                .receiverId(receiver.getId()) // Receiver ID
                .tripId(trip.getId())
                .productId(savedProduct.getId()) // Use the auto-generated product ID
                .price(BigDecimal.ZERO) // Default price, can be calculated based on business logic
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // Build response with usernames from retrieved User objects
        return OrderResponse.builder()
                .orderId(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .senderUsername(sender.getUsername())
                .receiverUsername(receiver.getUsername())
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

    /**
     * Create multiple orders for different receivers in a single batch
     * This optimizes the case where one user wants to send to multiple recipients
     */
    @Transactional
    public List<OrderResponse> createBatchOrders(BatchOrderRequest request) {
        log.info("Creating batch orders from sender: {} to {} recipients",
                request.getSenderIdentifier(), request.getRecipients().size());

        // Find sender by identifier (email or phone) to get userId
        User sender = findUserByIdentifier(request.getSenderIdentifier())
                .orElseThrow(() -> new RuntimeException("Sender not found with identifier: " + request.getSenderIdentifier()));

        // Validate all receivers exist before creating any orders
        List<User> receivers = new ArrayList<>();
        for (BatchOrderRequest.OrderRecipient recipient : request.getRecipients()) {
            User receiver = findUserByIdentifier(recipient.getReceiverIdentifier())
                    .orElseThrow(() -> new RuntimeException("Receiver not found with identifier: " + recipient.getReceiverIdentifier()));
            receivers.add(receiver);
        }

        // Validate robot is online and free using cached data
        validateRobotAvailability(request.getRobotCode(), request.getRobotContainerCode());

        // Create or find active trip for the robot (shared trip for all orders)
        Trip trip = findOrCreateActiveTrip(request.getRobotCode(), request.getRobotContainerCode(),
                                         request.getStartPoint(), request.getEndpoint(), sender.getId());

        List<OrderResponse> responses = new ArrayList<>();

        // Create orders for each recipient
        for (int i = 0; i < request.getRecipients().size(); i++) {
            BatchOrderRequest.OrderRecipient recipient = request.getRecipients().get(i);
            User receiver = receivers.get(i);

            // Create product for this order
            Product product = Product.builder()
                    .code(recipient.getProductName())
                    .tripId(trip.getId())
                    .containerCode(request.getRobotContainerCode())
                    .build();

            Product savedProduct = productRepository.save(product);

            // Generate unique order code
            String orderCode = orderCodeGenerator.generateOrderCode();

            // Create order
            Order order = Order.builder()
                    .orderCode(orderCode)
                    .userId(sender.getId())
                    .receiverId(receiver.getId())
                    .tripId(trip.getId())
                    .productId(savedProduct.getId())
                    .price(BigDecimal.ZERO)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            Order savedOrder = orderRepository.save(order);

            // Build response with usernames from retrieved User objects
            OrderResponse response = OrderResponse.builder()
                    .orderId(savedOrder.getId())
                    .orderCode(savedOrder.getOrderCode())
                    .senderUsername(sender.getUsername())
                    .receiverUsername(receiver.getUsername())
                    .productName(savedProduct.getCode())
                    .robotCode(request.getRobotCode())
                    .robotContainerCode(request.getRobotContainerCode())
                    .endpoint(request.getEndpoint())
                    .price(savedOrder.getPrice())
                    .status(savedOrder.getStatus())
                    .createdAt(savedOrder.getCreatedAt())
                    .completedAt(savedOrder.getCompletedAt())
                    .build();

            responses.add(response);
        }

        log.info("Successfully created {} orders in batch for sender: {}", responses.size(), request.getSenderIdentifier());
        return responses;
    }

    /**
     * Find user by email, phone number, or username
     * This method supports flexible user identification using email, phone, or username
     */
    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Optional.empty();
        }

        identifier = identifier.trim();

        // Check if identifier looks like an email (contains @)
        if (identifier.contains("@")) {
            log.debug("Looking up user by email: {}", identifier);
            return userRepository.findByEmail(identifier);
        } else {
            // First try to find by phone
            log.debug("Looking up user by phone: {}", identifier);
            Optional<User> userByPhone = userRepository.findByPhone(identifier);

            // If not found by phone, try by username
            if (userByPhone.isEmpty()) {
                log.debug("Not found by phone, looking up user by username: {}", identifier);
                return userRepository.findByUsername(identifier);
            }

            return userByPhone;
        }
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

    private Trip findOrCreateActiveTrip(String robotCode, String robotContainerCode, String startPoint, String endpoint, UUID userId) {
        // Look for an active trip for the robot code - uses JOIN query to avoid lazy loading issues
        Optional<Trip> activeTrip = tripRepository.findActiveByRobotCode(robotCode);

        if (activeTrip.isPresent()) {
            Trip existingTrip = activeTrip.get();
            // Update trip with start point, endpoint, user ID, and robot container ID if not already set
            if (existingTrip.getStartPoint() == null || existingTrip.getEndPoint() == null ||
                existingTrip.getUserId() == null || existingTrip.getRobotContainerId() == null) {

                if (existingTrip.getStartPoint() == null) {
                    existingTrip.setStartPoint(startPoint);
                }
                if (existingTrip.getEndPoint() == null) {
                    existingTrip.setEndPoint(endpoint);
                }
                if (existingTrip.getUserId() == null) {
                    existingTrip.setUserId(userId);
                }

                // Get and set robot container ID if not already set
                if (existingTrip.getRobotContainerId() == null) {
                    Long robotContainerId = getRobotContainerIdByCode(robotCode, robotContainerCode);
                    existingTrip.setRobotContainerId(robotContainerId);
                }

                return tripRepository.save(existingTrip);
            }
            return existingTrip;
        }

        // For new trip creation, we need the actual robot UUID and container ID from database
        UUID robotId = getRobotIdByCode(robotCode);
        Long robotContainerId = getRobotContainerIdByCode(robotCode, robotContainerCode);

        // Generate unique trip code
        String tripCode = tripCodeGenerator.generateTripCode();

        // Create new trip if no active trip exists - starts with PENDING status
        Trip newTrip = Trip.builder()
                .tripCode(tripCode)
                .robotId(robotId)
                .robotContainerId(robotContainerId)
                .userId(userId)
                .startPoint(startPoint)
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

    private Long getRobotContainerIdByCode(String robotCode, String robotContainerCode) {
        // Get robot container ID for trip creation
        return robotContainerRepository.findByRobotCodeAndContainerCode(robotCode, robotContainerCode)
                .map(robotContainer -> robotContainer.getId())
                .orElseThrow(() -> new RuntimeException("Robot container not found with robot code: " + robotCode + " and container code: " + robotContainerCode));
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

    /**
     * Get orders where the user is the sender
     */
    public List<OrderResponse> getOrdersBySender(String senderUsername) {
        log.info("Retrieving orders sent by: {}", senderUsername);

        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("Sender not found with username: " + senderUsername));

        List<Order> orders = orderRepository.findByUserId(sender.getId());

        return orders.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get orders where the user is the receiver
     */
    public List<OrderResponse> getOrdersByReceiver(String receiverUsername) {
        log.info("Retrieving orders received by: {}", receiverUsername);

        User receiver = userRepository.findByUsername(receiverUsername)
                .orElseThrow(() -> new RuntimeException("Receiver not found with username: " + receiverUsername));

        List<Order> orders = orderRepository.findByReceiverId(receiver.getId());

        return orders.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get order by order code
     * @param orderCode Order code to search for
     * @return OrderResponse containing order details including price
     */
    public OrderResponse getOrderByOrderCode(String orderCode) {
        log.info("Retrieving order by order code: {}", orderCode);

        Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with code: " + orderCode);
        }

        Order order = orderOpt.get();
        return convertToOrderResponse(order);
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

        // Get robot code, start point, and endpoint from trip information
        String robotCode = "N/A";
        String startPoint = "N/A";
        String endpoint = "N/A";
        if (order.getTripId() != null) {
            Optional<Trip> trip = tripRepository.findById(order.getTripId());
            if (trip.isPresent()) {
                startPoint = trip.get().getStartPoint();
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

        // Get sender and receiver usernames
        String senderUsername = "N/A";
        String receiverUsername = "N/A";

        if (order.getUserId() != null) {
            Optional<User> sender = userRepository.findById(order.getUserId());
            if (sender.isPresent()) {
                senderUsername = sender.get().getUsername();
            }
        }

        if (order.getReceiverId() != null) {
            Optional<User> receiver = userRepository.findById(order.getReceiverId());
            if (receiver.isPresent()) {
                receiverUsername = receiver.get().getUsername();
            }
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .senderUsername(senderUsername)
                .receiverUsername(receiverUsername)
                .productName(productName) // Product name from product table
                .robotCode(robotCode) // Robot code from robot table via trip
                .robotContainerCode(robotContainerCode) // Container code from product table
                .startPoint(startPoint) // Start point from trip table
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
