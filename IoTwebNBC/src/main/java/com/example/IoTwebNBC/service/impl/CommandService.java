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
            String topic = "devices/" + room + "/commands/" + device;
            String payload = action;
            mqtt.send(topic, payload, 1, false);   // QoS1, not retained
        } catch (Exception e) {
            throw new RuntimeException("Publish command failed", e);
        }
    }

}