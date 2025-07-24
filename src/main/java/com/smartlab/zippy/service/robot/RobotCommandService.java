package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import org.springframework.stereotype.Service;

@Service
public class RobotCommandService {

    private final MqttCommandPublisher publisher;

    public RobotCommandService(MqttCommandPublisher publisher) {
        this.publisher = publisher;
    }

    public void sendMove(String robotId, double lat, double lon) {
        String topic = String.format("robot/%s/command/move", robotId);
        String payload = String.format("{\"lat\":%.6f,\"lon\":%.6f}", lat, lon);
        publisher.publish(payload, topic);
    }

    public void sendPickup(String robotId, String containerCode, boolean pickup) {
        String topic = String.format("robot/%s/container/%s/command/pickup", robotId, containerCode);
        String payload = pickup ? "1" : "0";
        publisher.publish(topic, payload);
    }

    public void sendLoad(String robotId, String containerCode, boolean load) {
        String topic = String.format("robot/%s/container/%s/command/load", robotId, containerCode);
        String payload = load ? "1" : "0";
        publisher.publish(topic, payload);
    }
}
