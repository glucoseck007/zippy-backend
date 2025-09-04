package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.model.dto.robot.RobotDTO;
import com.smartlab.zippy.model.entity.Robot;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class RobotService {

    private final RobotRepository robotRepository;

    /**
     * Get all robots
     *
     * @return List of all robots
     */
    @Transactional(readOnly = true)
    public List<RobotDTO> getAllRobots() {
        log.info("Fetching all robots");

        Iterable<Robot> robots = robotRepository.findAll();
        List<RobotDTO> robotDTOs = StreamSupport.stream(robots.spliterator(), false)
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        log.info("Found {} robots", robotDTOs.size());
        return robotDTOs;
    }

    /**
     * Get robot by ID
     *
     * @param id Robot ID
     * @return Robot DTO
     */
    @Transactional(readOnly = true)
    public RobotDTO getRobotById(UUID id) {
        log.info("Fetching robot with ID: {}", id);

        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Robot not found with ID: " + id));

        return convertToDTO(robot);
    }

    /**
     * Get robots by battery level threshold
     *
     * @param batteryThreshold Battery level threshold (0.0 to 100.0)
     * @param isLowBattery true to get robots below threshold, false to get robots above threshold
     * @return List of robots matching battery criteria
     */
    @Transactional(readOnly = true)
    public List<RobotDTO> getRobotsByBatteryLevel(double batteryThreshold, boolean isLowBattery) {
        log.info("Fetching robots with battery {} threshold: {}",
                isLowBattery ? "below" : "above", batteryThreshold);

        List<Robot> allRobots = (List<Robot>) robotRepository.findAll();
        List<RobotDTO> filteredRobots = allRobots.stream()
                .filter(robot -> isLowBattery ?
                    robot.getBatteryStatus() < batteryThreshold :
                    robot.getBatteryStatus() >= batteryThreshold)
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        log.info("Found {} robots with battery {} threshold: {}",
                filteredRobots.size(), isLowBattery ? "below" : "above", batteryThreshold);
        return filteredRobots;
    }

    /**
     * Get robots with low battery (below 20%)
     *
     * @return List of robots with low battery
     */
    @Transactional(readOnly = true)
    public List<RobotDTO> getLowBatteryRobots() {
        return getRobotsByBatteryLevel(20.0, true);
    }

    /**
     * Get available robots (robots that are not currently on a trip)
     *
     * @return List of available robots
     */
    @Transactional(readOnly = true)
    public List<RobotDTO> getAvailableRobots() {
        log.info("Fetching available robots");

        Iterable<Robot> allRobots = robotRepository.findAll();
        List<RobotDTO> availableRobots = StreamSupport.stream(allRobots.spliterator(), false)
                .filter(this::isRobotAvailable)
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        log.info("Found {} available robots", availableRobots.size());
        return availableRobots;
    }

    /**
     * Check if robot is available (not on active trips)
     *
     * @param robot Robot entity
     * @return true if robot is available
     */
    private boolean isRobotAvailable(Robot robot) {
        if (robot.getTrips() == null || robot.getTrips().isEmpty()) {
            return true;
        }

        // Check if robot has any active trips
        return robot.getTrips().stream()
                .noneMatch(trip -> isActiveTripStatus(trip.getStatus()));
    }

    /**
     * Check if trip status indicates an active trip
     *
     * @param status Trip status
     * @return true if trip is active
     */
    private boolean isActiveTripStatus(String status) {
        return status != null && (
                status.equalsIgnoreCase("CREATED") ||
                status.equalsIgnoreCase("IN_PROGRESS") ||
                status.equalsIgnoreCase("ASSIGNED")
        );
    }

    /**
     * Convert Robot entity to RobotDTO
     *
     * @param robot Robot entity
     * @return RobotDTO
     */
    private RobotDTO convertToDTO(Robot robot) {
        int containerCount = robot.getContainers() != null ? robot.getContainers().size() : 0;
        int activeTripsCount = robot.getTrips() != null ?
                (int) robot.getTrips().stream()
                        .filter(trip -> isActiveTripStatus(trip.getStatus()))
                        .count() : 0;

        String status = determineRobotStatus(robot, activeTripsCount);

        return RobotDTO.builder()
                .code(robot.getCode())
                .batteryStatus(robot.getBatteryStatus())
                .locationRealtime(robot.getLocationRealtime())
                .build();
    }

    /**
     * Determine robot status based on current state
     *
     * @param robot Robot entity
     * @param activeTripsCount Number of active trips
     * @return Status string
     */
    private String determineRobotStatus(Robot robot, int activeTripsCount) {
        // If battery is low (below 20%), mark as maintenance needed
        if (robot.getBatteryStatus() < 20.0) {
            return "MAINTENANCE";
        }

        // If robot has active trips, it's busy
        if (activeTripsCount > 0) {
            return "BUSY";
        }

        // If no location data, assume offline
        if (robot.getLocationRealtime() == null || robot.getLocationRealtime().isEmpty()) {
            return "OFFLINE";
        }

        // Otherwise, robot is available
        return "AVAILABLE";
    }
}
