package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends CrudRepository<Product, UUID> {
    Optional<Product> findByCode(String code);
    List<Product> findByTripId(UUID tripId);
    List<Product> findByContainerId(Long containerId);
}
