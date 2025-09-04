package com.smartlab.zippy.service.trip;

import com.smartlab.zippy.component.RobotStatusCache;
import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.robot.RobotTripMqttDTO;
import com.smartlab.zippy.model.dto.trip.TripStateMqttDTO;
import com.smartlab.zippy.model.dto.web.response.trip.TripProgressResponse;
import com.smartlab.zippy.model.dto.web.response.trip.TripResponse;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.RobotRepository;
import com.smartlab.zippy.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripStatusService {

    private final RobotStatusCache robotStatusCache;
    private final TripRepository tripRepository;
    private final RobotRepository robotRepository;
    private final MqttCommandPublisher mqttCommandPublisher;

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
        tripRepository.findByTripCode(tripCode).ifPresent(trip -> {
            trip.setStatus("CANCELLED");
            tripRepository.save(trip);
        });
        return TripResponse.builder()
                .robotCode(robotCode)
                .tripCode(tripCode)
                .status("CANCELLED")
                .build();
    }
}
