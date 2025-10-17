package com.example.IoTwebNBC.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "deviceaction")
public class DeviceActionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device")
    private String device;

    @Column(name = "room")
    private String room;

    @Column(name = "status")
    private String status;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

}
