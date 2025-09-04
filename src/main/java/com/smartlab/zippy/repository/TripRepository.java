package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Trip;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends CrudRepository<Trip, UUID> {
    List<Trip> findByUserId(UUID userId);

    List<Trip> findByRobotId(UUID robotId);

    @Query("SELECT t FROM Trip t JOIN FETCH t.robot JOIN Order o ON t.id = o.tripId WHERE o.orderCode = :orderCode")
    Optional<Trip> findTripByOrderCode(String orderCode);

    @Query("SELECT t FROM Trip t JOIN FETCH t.robot WHERE t.tripCode = :tripCode")
    Optional<Trip> findByTripCode(String tripCode);

    @Query("SELECT t FROM Trip t WHERE t.robotId = :robotId AND t.status = 'ACTIVE'")
    Optional<Trip> findActiveByRobotId(@Param("robotId") UUID robotId);

    @Query("SELECT t FROM Trip t JOIN Robot r ON t.robotId = r.id WHERE r.code = :robotCode AND t.status IN ('PENDING', 'ACTIVE')")
    Optional<Trip> findActiveByRobotCode(@Param("robotCode") String robotCode);

    @Query("SELECT t FROM Trip t JOIN t.robot r WHERE t.tripCode = :tripCode AND r.code = :robotCode")
    Optional<Trip> findByTripCodeAndRobotCode(@Param("tripCode") String tripCode, @Param("robotCode") String robotCode);

    List<Trip> findByStatusIn(List<String> statuses);

}
