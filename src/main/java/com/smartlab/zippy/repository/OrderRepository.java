package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends CrudRepository<Order, UUID> {
    List<Order> findByTripId(UUID tripId);
    List<Order> findByStatus(String status);
    Optional<Order> findByOrderCode(String orderCode);

    @Query("SELECT o FROM Order o " +
           "JOIN User u ON o.userId = u.id " +
           "WHERE u.username = :username " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUsername(@Param("username") String username);

    @Query("SELECT o FROM Order o " +
           "JOIN o.trip t " +
           "WHERE t.tripCode = :tripCode")
    Order findByTripCode(@Param("tripCode") String tripCode);

    // Query orders by sender ID (userId)
    List<Order> findByUserId(UUID userId);

    // Query orders by receiver ID
    List<Order> findByReceiverId(UUID receiverId);

    String id(UUID id);

    @Query("SELECT o FROM Order o WHERE o.status = 'QUEUED' ORDER BY o.createdAt ASC")
    Optional<Order> findOrderByCreatedAtAsc();

    Optional<Object> findFirstByStatusOrderByCreatedAtAsc(String queued);

    @Query("SELECT o FROM Order o JOIN o.trip t WHERE t.tripCode = :tripCode")
    Order getOrderByTripCode(String tripCode);
}
