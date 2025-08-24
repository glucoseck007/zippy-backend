package com.smartlab.zippy.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trip")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "trip_code", unique = true)
    private String tripCode;

    @Column(name = "start_point")
    private String startPoint;

    @Column(name = "end_point")
    private String endPoint;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "robot_id")
    private UUID robotId;
    
    @Column(name = "robot_container_id")
    private Long robotContainerId;

    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", insertable = false, updatable = false)
    private Robot robot;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_container_id", insertable = false, updatable = false)
    private RobotContainer robotContainer;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
    private List<Order> orders;
    
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
    private List<Product> products;
}
