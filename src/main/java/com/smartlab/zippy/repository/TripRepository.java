package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByUserId(UUID userId);
    List<Trip> findByRobotId(UUID robotId);
}
