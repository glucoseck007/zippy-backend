package com.smartlab.zippy.service.robot;

import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import org.springframework.stereotype.Service;

@Service
public class RobotCommandService {

    private final MqttCommandPublisher publisher;

    public RobotCommandService(MqttCommandPublisher publisher) {
        this.publisher = publisher;
    }

    public void sendMove(String robotId, double lat, double lon, String roomCode) {
        String topic = String.format("robot/%s/command/move", robotId);
        String payload = String.format("{\"lat\":%.6f,\"lon\":%.6f,\"roomCode\":\"%s\"}", lat, lon, roomCode);
        publisher.publish(payload, topic);
    }

    public void sendPickup(String robotId, String containerCode, boolean pickup) {
        String topic = String.format("robot/%s/container/%s/command/pickup", robotId, containerCode);
        String payload = pickup ? "1" : "0";
        publisher.publish(payload, topic);
    }

    public void sendLoad(String robotId, String containerCode, boolean load) {
        String topic = String.format("robot/%s/container/%s/command/load", robotId, containerCode);
        String payload = load ? "true" : "false";
        publisher.publish(payload, topic);
    }

    public void requestStatus(String robotId) {
        String topic = String.format("robot/%s/command/request-status", robotId);
        String payload = "{\"action\":\"request_status\"}";
        publisher.publish(payload, topic);
    }

    // Trip-based command methods
    public void sendTripMove(String robotId, String tripCode, double lat, double lon, String roomCode) {
        String topic = String.format("robot/%s/command/trip/%s/move", robotId, tripCode);
        String payload = String.format("{\"tripCode\":\"%s\",\"lat\":%.6f,\"lon\":%.6f,\"roomCode\":\"%s\"}", tripCode, lat, lon, roomCode);
        publisher.publish(payload, topic);
    }

    public void sendTripPickup(String robotId, String containerCode, String tripCode, boolean pickup) {
        String topic = String.format("robot/%s/container/%s/command/trip/%s/pickup", robotId, containerCode, tripCode);
        String payload = String.format("{\"tripCode\":\"%s\",\"pickup\":%s}", tripCode, pickup ? "true" : "false");
        publisher.publish(payload, topic);
    }

    public void sendTripLoad(String robotId, String containerCode, String tripCode, boolean load) {
        String topic = String.format("robot/%s/container/%s/command/trip/%s/load", robotId, containerCode, tripCode);
        String payload = String.format("{\"tripCode\":\"%s\",\"load\":%s}", tripCode, load ? "true" : "false");
        publisher.publish(payload, topic);
    }

    public void sendTripContinue(String robotId, String tripCode) {
        String topic = String.format("robot/%s/command/trip/%s/continue", robotId, tripCode);
        String payload = String.format("{\"tripCode\":\"%s\",\"action\":\"continue\"}", tripCode);
        publisher.publish(payload, topic);
    }
}
