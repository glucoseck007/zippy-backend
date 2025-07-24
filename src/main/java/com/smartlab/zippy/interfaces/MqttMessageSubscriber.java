package com.smartlab.zippy.interfaces;

public interface MqttMessageSubscriber {
    /**
     * Subscribe to a topic
     *
     * @param topic Topic to subscribe to
     */
    void subscribe(String topic);

    /**
     * Unsubscribe from a topic
     *
     * @param topic Topic to unsubscribe from
     */
    void unsubscribe(String topic);
}
