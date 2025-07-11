package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends CrudRepository<Order, UUID> {
    List<Order> findByTripId(UUID tripId);
    List<Order> findByStatus(String status);
}
