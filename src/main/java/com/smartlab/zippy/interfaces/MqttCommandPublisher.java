package com.smartlab.zippy.interfaces;

import com.smartlab.zippy.model.dto.robot.ContainerCmdDTO;
import com.smartlab.zippy.model.dto.trip.TripCommandMqttDTO;
import com.smartlab.zippy.model.dto.trip.TripRegisterMqttDTO;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttCommandPublisher {
    void publish(String data, @Header(MqttHeaders.TOPIC) String topic);

    default void publishLocationCommand(String robotCode, String roomCode) {
        String topic = String.format("robot/%s/location", robotCode);
        String payload = String.format("{\"roomCode\":\"%s\"}", roomCode);
        publish(payload, topic);
    }

    default void publishBatteryRequest(String robotCode) {
        String topic = String.format("robot/%s/battery", robotCode);
        String payload = "{\"request\":\"battery_status\"}";
        publish(payload, topic);
    }

    default void publishStatusCommand(String robotCode, String status) {
        String topic = String.format("robot/%s/status", robotCode);
        String payload = String.format("{\"status\":\"%s\"}", status);
        publish(payload, topic);
    }

    default void publishContainerCommand(String robotCode, String status, boolean isClosed) {
        String topic = String.format("robot/%s/container", robotCode);
        String payload = String.format("{\"status\":\"%s\",\"isClosed\":%s}", status, isClosed);
        publish(payload, topic);
    }

    default void publishTripCommand(String robotCode, TripCommandMqttDTO dto) {
        String topic = String.format("robot/%s/trip/command", robotCode);
        String payload = String.format(
            "{\"trip_id\":\"%s\",\"command_status\":%d}",
            dto.getTrip_id(), dto.getCommand_status()
        );
        publish(payload, topic);
    }

    default void publishTripRegisterCommand(String robotCode, TripRegisterMqttDTO dto) {
        String topic = String.format("robot/%s/trip/register", robotCode);
        String payload = String.format(
            "{\"trip_id\":\"%s\",\"start_point\":\"%s\",\"end_point\":\"%s\"}",
            dto.getTrip_id(), dto.getStart_point(), dto.getEnd_point()
        );
        publish(payload, topic);
    }

    default void publishTripCancelCommand(String robotCode, String tripId) {
        String topic = String.format("robot/%s/trip/cancel", robotCode);
        String payload = String.format("{\"trip_id\":\"%s\"}", tripId);
        publish(payload, topic);
    }

    default void publishQrCodeCommand(String robotCode, String qrCodeBase64, int status) {
        String topic = String.format("robot/%s/qr-code", robotCode);
        String payload = String.format("{\"qr-code\":\"%s\",\"status\":%d}", qrCodeBase64, status);
        publish(payload, topic);
    }

    default void publishForceMoveCommand(String robotCode, String endPoint) {
        String topic = String.format("robot/%s/force_move", robotCode);
        String payload = String.format("{\"end_point\":\"%s\"}", endPoint);
        publish(payload, topic);
    }

    default void publishWarning(String robotCode, String title, String message, String timestamp) {
        String topic = String.format("robot/%s/warning", robotCode);
        String payload = String.format(
            "{\"title\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            title, message, timestamp
        );
        publish(payload, topic);
    }

    default void publishContainerCmd(String robotCode, ContainerCmdDTO dto) {
        String topic = String.format("robot/%s/container/cmd", robotCode);
        String payload = String.format("{\"lock\":%d}",dto.getLock());
        publish(payload, topic);
    }
}
