package com.smartlab.zippy.service.trip;

import com.smartlab.zippy.model.dto.web.response.trip.TripResponse;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final RobotRepository robotRepository;

    public TripResponse getTripByOrderId(UUID orderId) {
        log.info("Retrieving trip for orderId: {}", orderId);

        // Find the order first
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with ID: " + orderId);
        }

        Order order = orderOpt.get();
        return buildTripResponse(order, orderId.toString());
    }

    public TripResponse getTripByOrderCode(String orderCode) {
        log.info("Retrieving trip for orderCode: {}", orderCode);

        // Find the order by order code
        Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with code: " + orderCode);
        }

        Order order = orderOpt.get();
        return buildTripResponse(order, orderCode);
    }

    public TripResponse getTripByCode(String tripCode) {
        log.info("Retrieving trip for tripCode: {}", tripCode);

        // Find the trip directly by trip code
        Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
        if (tripOpt.isEmpty()) {
            throw new RuntimeException("Trip not found with code: " + tripCode);
        }

        Trip trip = tripOpt.get();

        // Get robot code if robot ID exists
        String robotCode = "N/A";
        if (trip.getRobotId() != null) {
            Optional<Robot> robot = robotRepository.findById(trip.getRobotId());
            if (robot.isPresent()) {
                robotCode = robot.get().getCode();
            }
        }

        return TripResponse.builder()
                .robotCode(robotCode)
                .tripCode(trip.getTripCode())
                .startPoint(trip.getStartPoint())
                .endPoint(trip.getEndPoint())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus())
                .build();
    }

    private TripResponse buildTripResponse(Order order, String orderIdentifier) {
        // Get trip information using the tripId from the order
        if (order.getTripId() == null) {
            throw new RuntimeException("No trip associated with order: " + orderIdentifier);
        }

        Optional<Trip> tripOpt = tripRepository.findById(order.getTripId());
        if (tripOpt.isEmpty()) {
            throw new RuntimeException("Trip not found with ID: " + order.getTripId());
        }

        Trip trip = tripOpt.get();

        // Get robot code if robot ID exists
        String robotCode = "N/A";
        if (trip.getRobotId() != null) {
            Optional<Robot> robot = robotRepository.findById(trip.getRobotId());
            if (robot.isPresent()) {
                robotCode = robot.get().getCode();
            }
        }

        return TripResponse.builder()
                .robotCode(robotCode)
                .tripCode(trip.getTripCode())
                .startPoint(trip.getStartPoint())
                .endPoint(trip.getEndPoint())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus())
                .build();
    }
}
