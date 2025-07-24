package com.smartlab.zippy.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "robot_container")
public class RobotContainer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "robot_id")
    private UUID robotId;

    @Column(name = "container_code", unique = true)
    private String containerCode;

    private String status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", insertable = false, updatable = false)
    private Robot robot;
    
    @OneToMany(mappedBy = "container", cascade = CascadeType.ALL)
    private List<Product> products;
}
