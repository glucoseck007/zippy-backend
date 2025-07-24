package com.smartlab.zippy.repository;

import com.smartlab.zippy.model.entity.RobotContainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RobotContainerRepository extends CrudRepository<RobotContainer, Long> {
    List<RobotContainer> findByRobotId(UUID robotId);
    List<RobotContainer> findByStatus(String status);

    @Query("SELECT rc FROM RobotContainer rc JOIN rc.robot r WHERE r.code = :robotCode AND rc.containerCode = :containerCode")
    Optional<RobotContainer> findByRobotCodeAndContainerCode(@Param("robotCode") String robotCode, @Param("containerCode") String containerCode);
}
