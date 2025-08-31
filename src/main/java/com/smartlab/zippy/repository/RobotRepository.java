package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Robot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RobotRepository extends CrudRepository<Robot, UUID> {
    Optional<Robot> findByCode(String code);
    List<Robot> findByRoomCode(String roomCode);

    // New double-based battery query methods
    List<Robot> findByBatteryStatusLessThan(double batteryLevel);
    List<Robot> findByBatteryStatusGreaterThanEqual(double batteryLevel);
    List<Robot> findByBatteryStatusBetween(double minLevel, double maxLevel);

    @Query("SELECT r FROM Robot r WHERE r.batteryStatus < :threshold")
    List<Robot> findLowBatteryRobots(@Param("threshold") double threshold);
}
