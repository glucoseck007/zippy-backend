package com.smartlab.zippy.interfaces;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttCommandPublisher {

    /**
     * Publish message to a specific MQTT topic
     * @param data Message payload
     * @param topic MQTT topic
     */
    void publish(String data, @Header(MqttHeaders.TOPIC) String topic);

    /**
     * Publish location command to robot
     * @param robotCode Robot identifier
     * @param roomCode Target room code
     */
    default void publishLocationCommand(String robotCode, String roomCode) {
        String topic = String.format("robot/%s/location", robotCode);
        String payload = String.format("{\"roomCode\":\"%s\"}", roomCode);
        publish(payload, topic);
    }

    /**
     * Publish battery status request to robot
     * @param robotCode Robot identifier
     */
    default void publishBatteryRequest(String robotCode) {
        String topic = String.format("robot/%s/battery", robotCode);
        String payload = "{\"request\":\"battery_status\"}";
        publish(payload, topic);
    }

    /**
     * Publish status command to robot
     * @param robotCode Robot identifier
     * @param status Target status ("free" or "non-free")
     */
    default void publishStatusCommand(String robotCode, String status) {
        String topic = String.format("robot/%s/status", robotCode);
        String payload = String.format("{\"status\":\"%s\"}", status);
        publish(payload, topic);
    }

    /**
     * Publish container command to robot
     * @param robotCode Robot identifier
     * @param status Container status ("free" or "non-free")
     * @param isClosed Whether container should be closed
     */
    default void publishContainerCommand(String robotCode, String status, boolean isClosed) {
        String topic = String.format("robot/%s/container", robotCode);
        String payload = String.format("{\"status\":\"%s\",\"isClosed\":%s}", status, isClosed);
        publish(payload, topic);
    }

    /**
     * Publish trip command to robot
     * @param robotCode Robot identifier
     * @param tripId Trip identifier
     * @param progress Trip progress (0.0 to 100.0)
     * @param status Trip status (0=Prepare, 1=Load, 2=OnGoing, 3=Delivered, 4=Finish)
     * @param startPoint Start location
     * @param endPoint End location
     */
    default void publishTripCommand(String robotCode, String tripId, double progress, int status, String startPoint, String endPoint) {
        String topic = String.format("robot/%s/trip", robotCode);
        String payload = String.format(
            "{\"trip_id\":\"%s\",\"progress\":%f,\"status\":%d,\"start_point\":\"%s\",\"end_point\":\"%s\"}",
            tripId, progress, status, startPoint, endPoint
        );
        publish(payload, topic);
    }

    /**
     * Publish QR code to robot
     * @param robotCode Robot identifier
     * @param qrCodeBase64 Base64 encoded QR code
     * @param status QR code status (0=Canceled, 1=Done, 2=Waiting)
     */
    default void publishQrCodeCommand(String robotCode, String qrCodeBase64, int status) {
        String topic = String.format("robot/%s/qr-code", robotCode);
        String payload = String.format("{\"qr-code\":\"%s\",\"status\":%d}", qrCodeBase64, status);
        publish(payload, topic);
    }

    /**
     * Publish force move command to robot
     * @param robotCode Robot identifier
     * @param endPoint Target destination
     */
    default void publishForceMoveCommand(String robotCode, String endPoint) {
        String topic = String.format("robot/%s/force_move", robotCode);
        String payload = String.format("{\"end_point\":\"%s\"}", endPoint);
        publish(payload, topic);
    }

    /**
     * Publish warning to robot
     * @param robotCode Robot identifier
     * @param title Warning title
     * @param message Warning message
     * @param timestamp Warning timestamp
     */
    default void publishWarning(String robotCode, String title, String message, String timestamp) {
        String topic = String.format("robot/%s/warning", robotCode);
        String payload = String.format(
            "{\"title\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            title, message, timestamp
        );
        publish(payload, topic);
    }
}
