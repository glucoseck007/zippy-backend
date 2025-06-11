package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.RobotContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RobotContainerRepository extends JpaRepository<RobotContainer, Long> {
    List<RobotContainer> findByRobotId(UUID robotId);
    List<RobotContainer> findByStatus(String status);
}
