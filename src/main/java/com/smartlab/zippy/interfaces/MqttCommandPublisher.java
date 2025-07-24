package com.smartlab.zippy.interfaces;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttCommandPublisher {
    void publish(String data, @Header(MqttHeaders.TOPIC) String topic);
}

