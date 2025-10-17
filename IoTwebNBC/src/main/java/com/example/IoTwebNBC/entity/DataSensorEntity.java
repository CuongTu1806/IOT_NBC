package com.example.IoTwebNBC.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "datasensor", indexes = {
        @Index(name="idx_room_ts", columnList="room,timestamp")
})
public class DataSensorEntity {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "temperature")
    private double temperature;

    @Column(name = "humidity")
    private double humidity;

    @Column(name = "light_level")
    private double lightLevel;

    @Column(name = "room")
    private String room;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
