package com.smartlab.zippy.service.order;

import com.smartlab.zippy.model.dto.trip.TripRegisterMqttDTO;
import com.smartlab.zippy.model.dto.web.request.order.OrderRequest;
import com.smartlab.zippy.model.dto.web.response.order.OrderResponse;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Product;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.ProductRepository;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.service.auth.UserService;
import com.smartlab.zippy.service.mqtt.MqttPublisherImpl;
import com.smartlab.zippy.service.robot.RobotMessageService;
import com.smartlab.zippy.service.robot.RobotStatusChangedEvent;
import com.smartlab.zippy.service.trip.TripCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderCodeGenerator orderGenerator = new OrderCodeGenerator();
    private final TripCodeGenerator tripGenerator = new TripCodeGenerator();

    private final OrderRepository orderRepository;
    private final TripRepository tripRepository;
    private final RobotRepository robotRepository;
    private final ProductRepository productRepository;

    private final UserService userService;
    private final RobotMessageService robotMessageService;

    private final MqttPublisherImpl mqttPublisher;

    private int validateRobot(String robotCode) {
        if (robotMessageService.isAlive(robotCode)) {
            if (robotMessageService.isRobotFree(robotCode)) {
                return 0; // Robot is available
            } else {
                return 1; // Robot is busy
            }
        } else {
            return 2; // Robot is offline
        }
    }

    public OrderResponse createOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderCode(orderGenerator.generateOrderCode());

        boolean flag1 = userService.isExistUser(orderRequest.getSenderIdentifier());
        boolean flag2 = userService.isExistUser(orderRequest.getReceiverIdentifier());

        int robotStatus = validateRobot(orderRequest.getRobotCode());
        String orderStatus = "QUEUED";
        if (robotStatus == 0) orderStatus = "ACTIVE";

        if (flag1 && flag2) {
            order.setSender(userService.getUserByCredential(orderRequest.getSenderIdentifier()).get());
            order.setReceiver(userService.getUserByCredential(orderRequest.getReceiverIdentifier()).get());
            order.setUserId(userService.getUserByCredential(orderRequest.getSenderIdentifier()).get().getId());
            order.setReceiverId(userService.getUserByCredential(orderRequest.getReceiverIdentifier()).get().getId());
            order.setStatus(orderStatus);
            order.setPrice(BigDecimal.valueOf(10000L));
            order.setCreatedAt(java.time.LocalDateTime.now());
            Trip trip = createTrip(orderRequest, orderStatus);
            TripRegisterMqttDTO dto = new TripRegisterMqttDTO();
            dto.setTrip_id(trip.getTripCode());
            dto.setStart_point(trip.getStartPoint());
            dto.setEnd_point(trip.getEndPoint());
            order.setTrip(trip);
            order.setTripId(trip.getId());
            Product product = createProduct(orderRequest, trip);
            order.setProductId(product.getId());
            orderRepository.save(order);
            mqttPublisher.publishTripRegisterCommand(orderRequest.getRobotCode(), dto);
            return OrderResponse.builder()
                    .orderCode(order.getOrderCode())
                    .status(order.getStatus())
                    .price(order.getPrice())
                    .createdAt(order.getCreatedAt())
                    .build();
        } else {
            return OrderResponse.builder().build();
        }
    }

    private Trip createTrip(OrderRequest orderRequest, String orderStatus) {
        Trip trip = new Trip();
        trip.setTripCode(tripGenerator.generateTripCode());
        trip.setStartPoint(orderRequest.getStartPoint());
        trip.setEndPoint(orderRequest.getEndPoint());
        Robot robot = robotRepository.findByCode(orderRequest.getRobotCode()).get();
        trip.setRobot(robot);
        trip.setRobotId(robot.getId());
        trip.setUserId(userService.getUserByCredential(orderRequest.getSenderIdentifier()).get().getId());
        trip.setUser(userService.getUserByCredential(orderRequest.getReceiverIdentifier()).get());
        trip.setStatus(orderStatus);
        trip.setStartTime(java.time.LocalDateTime.now());
        tripRepository.save(trip);
        return trip;
    }

    private Product createProduct(OrderRequest orderRequest, Trip trip) {
        Product product = new Product();
        product.setCode(orderRequest.getProductName());
        product.setTrip(trip);
        productRepository.save(product);
        return product;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrderByIdentifier(String identifier) {
        // Find user by credential (email or username)
        return userService.getUserByCredential(identifier)
                .map(user -> {
                    // Get orders where user is sender
                    List<Order> senderOrders = orderRepository.findByUserId(user.getId());

                    // Get orders where user is receiver
                    List<Order> receiverOrders = orderRepository.findByReceiverId(user.getId());

                    // Combine both lists
                    List<Order> allOrders = new ArrayList<>(senderOrders);
                    allOrders.addAll(receiverOrders);

                    // Convert to OrderResponse and sort by creation date (newest first)
                    return allOrders.stream()
                            .distinct() // Remove duplicates in case user is both sender and receiver
                            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                            .map(order -> {
                                // Build basic order response
                                OrderResponse.OrderResponseBuilder builder = OrderResponse.builder()
                                        .orderCode(order.getOrderCode())
                                        .status(order.getStatus())
                                        .price(order.getPrice())
                                        .createdAt(order.getCreatedAt());

                                // Add trip information (start point, end point, robot code)
                                if (order.getTripId() != null) {
                                    Optional<Trip> tripOpt = tripRepository.findById(order.getTripId());
                                    if (tripOpt.isPresent()) {
                                        Trip trip = tripOpt.get();
                                        builder.startPoint(trip.getStartPoint())
                                               .endpoint(trip.getEndPoint());

                                        // Get robot code from robot entity
                                        if (trip.getRobotId() != null) {
                                            Optional<Robot> robotOpt = robotRepository.findById(trip.getRobotId());
                                            if (robotOpt.isPresent()) {
                                                builder.robotCode(robotOpt.get().getCode());
                                            }
                                        }
                                    }
                                }

                                // Add product information
                                if (order.getProductId() != null) {
                                    Optional<Product> productOpt = productRepository.findById(order.getProductId());
                                    if (productOpt.isPresent()) {
                                        builder.productName(productOpt.get().getCode());
                                    }
                                }

                                return builder.build();
                            })
                            .toList();
                })
                .orElse(new ArrayList<>()); // Return empty list if user not found
    }

    public void dequeueOrder(String robotCode) {
        log.info("Starting dequeue process for robot: {}", robotCode);

        Optional<Order> pendingOrderOpt = orderRepository.findOrderByCreatedAtAsc();

        if (pendingOrderOpt.isPresent()) {
            Order order = pendingOrderOpt.get();
            log.info("Found pending order: {} with status: {}", order.getOrderCode(), order.getStatus());

            if (order.getStatus().equals("ACTIVE")) {
                log.debug("Order {} is already active, skipping", order.getOrderCode());
                return;
            }

            if (!order.getStatus().equals("QUEUED")) {
                log.warn("Order {} has unexpected status: {}, expected QUEUED", order.getOrderCode(), order.getStatus());
                return;
            }

            // Update order status to ACTIVE
            order.setStatus("ACTIVE");
            orderRepository.save(order);
            log.info("Updated order {} status to ACTIVE", order.getOrderCode());

            // Update associated trip status to ACTIVE
            Optional<Trip> tripOpt = tripRepository.findById(order.getTripId());
            if (tripOpt.isPresent()) {
                Trip trip = tripOpt.get();
                trip.setStatus("ACTIVE");

                tripRepository.save(trip);
                TripRegisterMqttDTO tripRegisterMqttDTO = new TripRegisterMqttDTO();
                tripRegisterMqttDTO.setTrip_id(trip.getTripCode());
                tripRegisterMqttDTO.setStart_point(trip.getStartPoint());
                tripRegisterMqttDTO.setEnd_point(trip.getEndPoint());

                log.info("Attempting to publish MQTT trip register command for robot: {} with trip: {} (start: {}, end: {})",
                    trip.getRobot().getCode(), trip.getTripCode(), trip.getStartPoint(), trip.getEndPoint());

                try {
                    mqttPublisher.publishTripRegisterCommand(trip.getRobot().getCode(), tripRegisterMqttDTO);
                    log.info("Successfully published MQTT trip register command for robot: {} with trip: {}",
                        trip.getRobot().getCode(), trip.getTripCode());
                } catch (Exception e) {
                    log.error("Failed to publish MQTT trip register command for robot: {} with trip: {}, error: {}",
                        trip.getRobot().getCode(), trip.getTripCode(), e.getMessage(), e);
                }

                log.info("Updated trip {} status to ACTIVE", trip.getTripCode());
            } else {
                log.error("Trip not found for order {}", order.getOrderCode());
            }

            log.info("Successfully assigned order {} to robot {}", order.getOrderCode(), robotCode);
        } else {
            log.debug("No pending orders found for robot {}", robotCode);
        }
    }

    @EventListener
    public void handleRobotStatusChangedEvent(RobotStatusChangedEvent event) {
        log.info("Received robot status change event for robot: {}, isAvailable: {}",
                event.getRobotCode(), event.isAvailable());

        if (event.isAvailable()) {
            log.info("Robot {} became available, triggering dequeue process", event.getRobotCode());
            dequeueOrder(event.getRobotCode());
        } else {
            log.debug("Robot {} is not available, skipping dequeue", event.getRobotCode());
        }
    }

    public Order getOrderByTripCode(String tripCode) {
        return orderRepository.findByTripCode(tripCode);
    }
}
