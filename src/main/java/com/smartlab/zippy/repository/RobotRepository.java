package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Robot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RobotRepository extends JpaRepository<Robot, Long> {
    Optional<Robot> findByCode(String code);
    List<Robot> findByBatteryStatus(String batteryStatus);
}
