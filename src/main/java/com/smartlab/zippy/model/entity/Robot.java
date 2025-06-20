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
@Table(name = "robot")
public class Robot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String code;
    
    @Column(name = "battery_status")
    private String batteryStatus;
    
    @Column(name = "location_realtime")
    private String locationRealtime;
    
    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    private List<Trip> trips;
    
    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    private List<RobotContainer> containers;
}
