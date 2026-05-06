package hu.u_szeged.inf.fog.simulator.iot;

import lombok.Getter;

@Getter
public enum TaskType {
    MEDICAL(6),
    TRAFFIC(3),
    WEATHER(1);

    private final int priority;

    TaskType(int priority) {
        this.priority = priority;
    }
}