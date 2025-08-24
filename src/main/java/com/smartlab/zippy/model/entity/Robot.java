package com.smartlab.zippy.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.GenericGenerator;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "robot")
@ToString(exclude = {"trips", "containers"}) // Exclude circular reference fields
public class Robot {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    
    private String code;
    
    @Column(name = "battery_status")
    private String batteryStatus;
    
    @Column(name = "location_realtime")
    private String locationRealtime;
    
    @Column(name = "room_code")
    private String roomCode;

    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    private List<Trip> trips;
    
    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    private List<RobotContainer> containers;
}
