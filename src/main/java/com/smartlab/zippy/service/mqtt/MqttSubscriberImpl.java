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

    // Regex patterns for topic matching based on new topic structure
    private static final Pattern LOCATION_PATTERN = Pattern.compile("robot/([^/]+)/location");
    private static final Pattern BATTERY_PATTERN = Pattern.compile("robot/([^/]+)/battery");
    private static final Pattern STATUS_PATTERN = Pattern.compile("robot/([^/]+)/status");
    private static final Pattern CONTAINER_PATTERN = Pattern.compile("robot/([^/]+)/container");
    private static final Pattern TRIP_PATTERN = Pattern.compile("robot/([^/]+)/trip");
    private static final Pattern TRIP_STATE_PATTERN = Pattern.compile("robot/([^/]+)/trip/state");
    private static final Pattern QR_CODE_PATTERN = Pattern.compile("robot/([^/]+)/qr-code");
    private static final Pattern FORCE_MOVE_PATTERN = Pattern.compile("robot/([^/]+)/force_move");
    private static final Pattern WARNING_PATTERN = Pattern.compile("robot/([^/]+)/warning");
    private static final Pattern HEARTBEAT_PATTERN = Pattern.compile("robot/([^/]+)/heartbeat");

    public MqttSubscriberImpl(
            MqttProperties mqttProperties, RobotMessageService robotMessageService) {
        this.robotMessageService = robotMessageService;
        this.brokerUrl = mqttProperties.getBroker();
        this.clientId = mqttProperties.getClientId() + "-subscriber";
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

            // Connection resilience settings
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
            subscribeToInboundTopics();

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
                log.info("Connected to MQTT broker at {}", brokerUrl);
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
                subscribeToInboundTopics();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Subscribe to all inbound topics for robot communication
     * Based on the topic structure: robot/+/[location|battery|status|container|trip|qr-code|force_move|warning]
     */
    private void subscribeToInboundTopics() {
        try {
            // Subscribe to all inbound topics using wildcards
            subscribe("robot/+/location");
            subscribe("robot/+/battery");
            subscribe("robot/+/status");
            subscribe("robot/+/container");
            subscribe("robot/+/trip");
            subscribe("robot/+/qr-code");
            subscribe("robot/+/force_move");
            subscribe("robot/+/warning");
            subscribe("robot/+/heartbeat");
            subscribe("robot/+/trip/state");

            log.info("Successfully subscribed to all inbound robot topics");
        } catch (Exception e) {
            log.error("Failed to subscribe to inbound robot topics", e);
        }
    }

    @Override
    public void subscribe(String topic) {
        try {
            mqttClient.subscribe(topic, 1); // QoS 1 for reliable delivery
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

    /**
     * Process incoming MQTT messages and route them to appropriate handlers
     * based on the topic structure and payload formats
     */
    private void processMessage(String topic, String payload) {
        try {
            log.info("Processing message from topic: {} with payload: {}", topic, payload);

            Matcher tripStateMatcher = TRIP_STATE_PATTERN.matcher(topic);
            if (tripStateMatcher.matches()) {
                String robotCode = tripStateMatcher.group(1);
                log.info("Received trip state message from robot: {} - {}", robotCode, payload);
                robotMessageService.handleTrip(robotCode, payload);
                return;
            }

            // Match topic with appropriate pattern and extract robot code
            Matcher locationMatcher = LOCATION_PATTERN.matcher(topic);
            if (locationMatcher.matches()) {
                String robotCode = locationMatcher.group(1);
                log.info("Received location message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleLocationMessage(robotCode, payload);
                robotMessageService.handleLocation(robotCode, payload);
                return;
            }

            Matcher batteryMatcher = BATTERY_PATTERN.matcher(topic);
            if (batteryMatcher.matches()) {
                String robotCode = batteryMatcher.group(1);
                log.info("Received battery message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleBattery(robotCode, payload);
                robotMessageService.handleBattery(robotCode, payload);
                return;
            }

            Matcher statusMatcher = STATUS_PATTERN.matcher(topic);
            if (statusMatcher.matches()) {
                String robotCode = statusMatcher.group(1);
                log.info("Received status message from robot: {} - {}", robotCode, payload);
                robotMessageService.handleStatus(robotCode, payload);
                return;
            }

            Matcher containerMatcher = CONTAINER_PATTERN.matcher(topic);
            if (containerMatcher.matches()) {
                String robotCode = containerMatcher.group(1);
                log.info("Received container message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleContainerStatus(robotCode, payload);
                robotMessageService.handleContainerStatus(robotCode, payload);
                return;
            }

            Matcher tripMatcher = TRIP_PATTERN.matcher(topic);
            if (tripMatcher.matches()) {
                String robotCode = tripMatcher.group(1);
                log.info("Received trip message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleTrip(robotCode, payload);
                robotMessageService.handleTripState(robotCode, payload);
                return;
            }

            Matcher qrCodeMatcher = QR_CODE_PATTERN.matcher(topic);
            if (qrCodeMatcher.matches()) {
                String robotCode = qrCodeMatcher.group(1);
                log.info("Received QR code message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleQRCode(robotCode, payload);
                robotMessageService.handleQRCode(robotCode, payload);
                return;
            }

            Matcher forceMoveMapping = FORCE_MOVE_PATTERN.matcher(topic);
            if (forceMoveMapping.matches()) {
                String robotCode = forceMoveMapping.group(1);
                log.info("Received force move message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleForceMoveMessage(robotCode, payload);
                return;
            }

            Matcher warningMatcher = WARNING_PATTERN.matcher(topic);
            if (warningMatcher.matches()) {
                String robotCode = warningMatcher.group(1);
                log.info("Received warning message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleWarningMessage(robotCode, payload);
                return;
            }

            Matcher heartbeatMatcher = HEARTBEAT_PATTERN.matcher(topic);
            if (heartbeatMatcher.matches()) {
                String robotCode = heartbeatMatcher.group(1);
                log.info("Received heartbeat message from robot: {} - {}", robotCode, payload);
                // TODO: Call robotMessageService.handleHeartbeat(robotCode, payload);
                robotMessageService.handleHeartbeat(robotCode, payload);
                return;
            }

            // If no pattern matches, log as unhandled
            log.warn("Received message on unhandled topic: {} with payload: {}", topic, payload);
        } catch (Exception e) {
            log.error("Error processing message from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT subscriber disconnected and closed");
            }
        } catch (MqttException e) {
            log.error("Error during MQTT client shutdown", e);
        }
    }
}
