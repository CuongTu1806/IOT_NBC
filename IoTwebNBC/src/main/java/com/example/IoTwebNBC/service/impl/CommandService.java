package com.example.IoTwebNBC.service.impl;

import com.example.IoTwebNBC.config.MqttConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommandService {

    private final MqttConfig.MqttGateway mqtt;

    private final ObjectMapper om = new ObjectMapper();

    public void sendCommand(String room, String device, String action) {
        try {
            String topic = room + "/commands"; // ví dụ: "room1/commands"
            String payload = om.writeValueAsString(Map.of(
                    "device", device,
                    "action", action
            ));
            System.out.println("Publishing to " + topic + " payload=" + payload);
            mqtt.send(topic, payload, 1, false);   // QoS1, not retained
        } catch (Exception e) {
            throw new RuntimeException("Publish command failed", e);
        }
    }

}