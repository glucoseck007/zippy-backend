package com.smartlab.zippy.service.trip;

import com.smartlab.zippy.component.RobotStatusCache;
import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.robot.RobotContainerMqttDTO;
import com.smartlab.zippy.model.dto.robot.RobotTripMqttDTO;
import com.smartlab.zippy.model.dto.trip.TripCommandMqttDTO;
import com.smartlab.zippy.model.dto.trip.TripStateMqttDTO;
import com.smartlab.zippy.model.dto.web.response.trip.TripProgressResponse;
import com.smartlab.zippy.model.dto.web.response.trip.TripResponse;
import com.smartlab.zippy.model.entity.Order;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.OrderRepository;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import com.smartlab.zippy.service.mqtt.MqttPublisherImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TripStatusService {

    private final RobotStatusCache robotStatusCache;
    private final TripRepository tripRepository;
    private final RobotRepository robotRepository;
    private final OrderRepository orderRepository;
    private final MqttPublisherImpl mqttCommandPublisher;

    public TripProgressResponse getTripProgressResponse(String tripCode) {
        Robot robot = robotRepository.findRobotByTripCode(tripCode).get();
        Trip trip = tripRepository.findByTripCode(tripCode).get();
        TripStateMqttDTO dto = robotStatusCache.getTrip(robot.getCode());
        return TripProgressResponse.builder()
                .robotCode(robot.getCode())
                .status(dto.getStatus())
                .tripCode(tripCode)
                .startPoint(dto.getStart_point())
                .endPoint(dto.getEnd_point())
                .progress(dto.getProgress())
                .startTime(trip.getStartTime())
                .build();
    }

    public double getTripProgress(String tripCode) {
        Robot robot = robotRepository.findRobotByTripCode(tripCode).get();
        TripStateMqttDTO dto = robotStatusCache.getTrip(robot.getCode());
        return dto.getProgress();
    }

    public TripResponse cancelTrip(String tripCode) {
        Robot robot = robotRepository.findRobotByTripCode(tripCode).get();
        String robotCode = robot.getCode();
        mqttCommandPublisher.publishTripCancelCommand(robotCode, tripCode);
        mqttCommandPublisher.publishQrCodeCommand(robotCode, null, 0);
        tripRepository.findByTripCode(tripCode).ifPresent(trip -> {
            trip.setStatus("CANCELLED");
            tripRepository.save(trip);
            Order order = orderRepository.findByTripCode(trip.getTripCode());
            order.setStatus("CANCELLED");
            orderRepository.save(order);
        });
        return TripResponse.builder()
                .robotCode(robotCode)
                .tripCode(tripCode)
                .status("CANCELLED")
                .build();
    }

    public TripResponse continueTrip(String tripCode) {
        Robot robot = robotRepository.findRobotByTripCode(tripCode)
                .orElseThrow(() -> new IllegalArgumentException("Robot not found for tripCode: " + tripCode));

        String robotCode = robot.getCode();

        Optional<Trip> tripOpt = tripRepository.findByTripCode(tripCode);
        if (tripOpt.isEmpty()) {
            return TripResponse.builder()
                    .robotCode(robotCode)
                    .tripCode(tripCode)
                    .status("TRIP_NOT_FOUND")
                    .build();
        }

        Trip trip = tripOpt.get();

        RobotContainerMqttDTO containerStatus = robotStatusCache.getContainerStatus(robotCode);
        boolean status = containerStatus.getStatus().equalsIgnoreCase("free");

        // Build trip command
        TripCommandMqttDTO dto = new TripCommandMqttDTO();
        dto.setTrip_id(tripCode);

        if ("LOADING".equals(trip.getStatus()) && status) {
            dto.setCommand_status(2);
            trip.setStatus("ONGOING");
        } else if ("DELIVERED".equals(trip.getStatus()) && status) {
            dto.setCommand_status(4);
            trip.setStatus("FINISHED");
        }

        // Check container status
        RobotContainerMqttDTO containerMqttDTO = robotStatusCache.getContainerStatus(robotCode);
        if (containerMqttDTO == null) {
            return TripResponse.builder()
                    .robotCode(robotCode)
                    .tripCode(tripCode)
                    .startPoint(trip.getStartPoint())
                    .endPoint(trip.getEndPoint())
                    .status("CONTAINER_STATUS_UNKNOWN")
                    .build();
        }

        if (!containerMqttDTO.isClosed()) {
            return TripResponse.builder()
                    .robotCode(robotCode)
                    .tripCode(tripCode)
                    .startPoint(trip.getStartPoint())
                    .endPoint(trip.getEndPoint())
                    .status("CONTAINER_NOT_CLOSED")
                    .build();
        }

        // If closed, publish command
        mqttCommandPublisher.publishTripCommand(robotCode, dto);
        tripRepository.save(trip);

        return TripResponse.builder()
                .robotCode(robotCode)
                .startPoint(trip.getStartPoint())
                .endPoint(trip.getEndPoint())
                .tripCode(tripCode)
                .status(trip.getStatus()) // return updated trip status
                .build();
    }


}
