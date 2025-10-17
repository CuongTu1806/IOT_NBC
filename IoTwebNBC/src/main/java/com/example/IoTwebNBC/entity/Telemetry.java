package com.example.IoTwebNBC.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry", indexes = {
        @Index(name="idx_device_ts", columnList="deviceId,ts")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Telemetry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private Double temp;
    private Double hum;

    // thời gian cảm biến (hiển thị theo VN – quy đổi ở handler)
    private LocalDateTime ts;
}
