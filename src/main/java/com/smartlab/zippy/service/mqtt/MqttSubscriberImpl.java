package com.smartlab.zippy.service.mqtt;

import com.smartlab.zippy.config.MqttProperties;
import com.smartlab.zippy.interfaces.MqttMessageSubscriber;
import com.smartlab.zippy.service.robot.RobotMessageService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MqttSubscriberImpl implements MqttMessageSubscriber {

    private final RobotMessageService robotMessageService;
    private final String brokerUrl;
    private final String clientId;
    private final String username;
    private final String password;

    private MqttClient mqttClient;

    // Regex patterns for topic matching
    private static final Pattern CONTAINER_STATUS_PATTERN =
            Pattern.compile("robot/([^/]+)/container/([^/]+)/status");
    private static final Pattern LOCATION_PATTERN =
            Pattern.compile("robot/([^/]+)/location");
    private static final Pattern BATTERY_PATTERN =
            Pattern.compile("robot/([^/]+)/battery");
    private static final Pattern STATUS_PATTERN =
            Pattern.compile("robot/([^/]+)/status");
    private static final Pattern TRIP_STATUS_PATTERN =
            Pattern.compile("robot/([^/]+)/trip/([^/]+)");
    private static final Pattern TRIP_START_POINT_PATTERN =
            Pattern.compile("robot/([^/]+)/trip/([^/]+)/start_point");
    private static final Pattern TRIP_END_POINT_PATTERN =
            Pattern.compile("robot/([^/]+)/trip/([^/]+)/end_point");

    public MqttSubscriberImpl(
            RobotMessageService robotMessageService,
            MqttProperties mqttProperties) {
        this.robotMessageService = robotMessageService;
        this.brokerUrl = mqttProperties.getBroker();
        this.clientId = mqttProperties.getClientId() + "-sub";
        this.username = mqttProperties.getUsername();
        this.password = mqttProperties.getPassword();
    }

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            if (username != null && !username.isEmpty()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }

            // Optional: resilience
            options.setKeepAliveInterval(30);
            options.setConnectionTimeout(60);
            options.setAutomaticReconnect(true);
            options.setMaxReconnectDelay(1000);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("Connection to MQTT broker lost", cause);
                    reconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    log.debug("Received message on topic {}: {}", topic, payload);
                    processMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used for subscribers
                }
            });

            connect();
            subscribeToRobotTopics();

        } catch (MqttException e) {
            log.error("Failed to initialize MQTT subscriber", e);
        }
    }

    private void connect() {
        try {
            if (!mqttClient.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);

                if (username != null && !username.isEmpty()) {
                    options.setUserName(username);
                    options.setPassword(password.toCharArray());
                }

                mqttClient.connect(options);
                log.info("Connected to MQTT broker");
            }
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker", e);
        }
    }

    private void reconnect() {
        try {
            Thread.sleep(5000); // Wait before reconnecting
            connect();
            if (mqttClient.isConnected()) {
                subscribeToRobotTopics();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void subscribeToRobotTopics() {
        try {
            // Subscribe to all robot topics using wildcards
            subscribe("robot/+/container/+/status");
            subscribe("robot/+/location");
            subscribe("robot/+/battery");
            subscribe("robot/+/status");
            subscribe("robot/+/trip/+");
            log.info("Subscribed to robot topics");
        } catch (Exception e) {
            log.error("Failed to subscribe to robot topics", e);
        }
    }

    @Override
    public void subscribe(String topic) {
        try {
            mqttClient.subscribe(topic);
            log.debug("Subscribed to topic: {}", topic);
        } catch (MqttException e) {
            log.error("Failed to subscribe to topic: {}", topic, e);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        try {
            mqttClient.unsubscribe(topic);
            log.debug("Unsubscribed from topic: {}", topic);
        } catch (MqttException e) {
            log.error("Failed to unsubscribe from topic: {}", topic, e);
        }
    }

    private void processMessage(String topic, String payload) {
        // Match topic with appropriate pattern and route to correct handler
        Matcher containerStatusMatcher = CONTAINER_STATUS_PATTERN.matcher(topic);
        if (containerStatusMatcher.matches()) {
            String robotId = containerStatusMatcher.group(1);
            String containerCode = containerStatusMatcher.group(2);
            robotMessageService.handleContainerStatus(robotId, containerCode, payload);
            return;
        }

        Matcher locationMatcher = LOCATION_PATTERN.matcher(topic);
        if (locationMatcher.matches()) {
            String robotId = locationMatcher.group(1);
            robotMessageService.handleLocation(robotId, payload);
            return;
        }

        Matcher batteryMatcher = BATTERY_PATTERN.matcher(topic);
        if (batteryMatcher.matches()) {
            String robotId = batteryMatcher.group(1);
            robotMessageService.handleBattery(robotId, payload);
            return;
        }

        Matcher statusMatcher = STATUS_PATTERN.matcher(topic);
        if (statusMatcher.matches()) {
            String robotId = statusMatcher.group(1);
            robotMessageService.handleStatus(robotId, payload);
            return;
        }

        Matcher tripStatusMatcher = TRIP_STATUS_PATTERN.matcher(topic);
        if (tripStatusMatcher.matches()) {
            String robotId = tripStatusMatcher.group(1);
            String tripId = tripStatusMatcher.group(2);
            robotMessageService.handleTripStatus(robotId, tripId, payload);
            return;
        }

//        Matcher tripStartPointMatcher = TRIP_START_POINT_PATTERN.matcher(topic);
//        if (tripStartPointMatcher.matches()) {
//            String robotId = tripStartPointMatcher.group(1);
//            String tripId = tripStartPointMatcher.group(2);
//            robotMessageService.handleTripStartPoint(robotId, tripId, payload);
//            return;
//        }
//
//        Matcher tripEndPointMatcher = TRIP_END_POINT_PATTERN.matcher(topic);
//        if (tripEndPointMatcher.matches()) {
//            String robotId = tripEndPointMatcher.group(1);
//            String tripId = tripEndPointMatcher.group(2);
//            robotMessageService.handleTripEndPoint(robotId, tripId, payload);
//            return;
//        }

        log.warn("Received message on unhandled topic: {}", topic);
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            log.error("Error during MQTT client shutdown", e);
        }
    }
}