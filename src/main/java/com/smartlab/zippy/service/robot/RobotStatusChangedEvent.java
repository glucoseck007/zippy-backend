package com.smartlab.zippy.service.robot;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RobotStatusChangedEvent extends ApplicationEvent {
    private final String robotCode;
    private final boolean isAvailable;

    public RobotStatusChangedEvent(Object source, String robotCode, boolean isAvailable) {
        super(source);
        this.robotCode = robotCode;
        this.isAvailable = isAvailable;
    }

}
