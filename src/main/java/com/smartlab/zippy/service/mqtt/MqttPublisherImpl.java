package com.smartlab.zippy.service.mqtt;


import com.smartlab.zippy.interfaces.MqttCommandPublisher;
import com.smartlab.zippy.model.dto.robot.ContainerCmdDTO;
import com.smartlab.zippy.model.dto.trip.TripCommandMqttDTO;
import com.smartlab.zippy.model.dto.trip.TripRegisterMqttDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttPublisherImpl implements MqttCommandPublisher {

    @Autowired
    private MessageChannel mqttOutboundChannel;

    @Override
    public void publish(String data, String topic) {
        try {
            log.info("Publishing MQTT message to topic: {} with payload: {}", topic, data);

            mqttOutboundChannel.send(
                MessageBuilder.withPayload(data)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build()
            );

            log.info("Successfully published MQTT message to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to publish MQTT message to topic: {}, error: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to publish MQTT message", e);
        }
    }

    @Override
    public void publishLocationCommand(String robotCode, String roomCode) {
        MqttCommandPublisher.super.publishLocationCommand(robotCode, roomCode);
    }

    @Override
    public void publishBatteryRequest(String robotCode) {
        MqttCommandPublisher.super.publishBatteryRequest(robotCode);
    }

    @Override
    public void publishStatusCommand(String robotCode, String status) {
        MqttCommandPublisher.super.publishStatusCommand(robotCode, status);
    }

    @Override
    public void publishContainerCommand(String robotCode, String status, boolean isClosed) {
        MqttCommandPublisher.super.publishContainerCommand(robotCode, status, isClosed);
    }

    @Override
    public void publishTripCommand(String robotCode, TripCommandMqttDTO dto) {
        log.info("Publishing trip command for robot: {} with trip: {} and command status: {}",
            robotCode, dto.getTrip_id(), dto.getCommand_status());
        MqttCommandPublisher.super.publishTripCommand(robotCode, dto);
    }

    @Override
    public void publishTripRegisterCommand(String robotCode, TripRegisterMqttDTO dto) {
        log.info("Publishing trip register command for robot: {} with trip: {}", robotCode, dto.getTrip_id());
        MqttCommandPublisher.super.publishTripRegisterCommand(robotCode, dto);
    }

    @Override
    public void publishTripCancelCommand(String robotCode, String tripId) {
        MqttCommandPublisher.super.publishTripCancelCommand(robotCode, tripId);
    }

    @Override
    public void publishQrCodeCommand(String robotCode, String qrCodeBase64, int status) {
        MqttCommandPublisher.super.publishQrCodeCommand(robotCode, qrCodeBase64, status);
    }

    @Override
    public void publishForceMoveCommand(String robotCode, String endPoint) {
        MqttCommandPublisher.super.publishForceMoveCommand(robotCode, endPoint);
    }

    @Override
    public void publishWarning(String robotCode, String title, String message, String timestamp) {
        MqttCommandPublisher.super.publishWarning(robotCode, title, message, timestamp);
    }

    @Override
    public void publishContainerCmd(String topic, ContainerCmdDTO dto) {
        MqttCommandPublisher.super.publishContainerCmd(topic, dto);
    }
}
