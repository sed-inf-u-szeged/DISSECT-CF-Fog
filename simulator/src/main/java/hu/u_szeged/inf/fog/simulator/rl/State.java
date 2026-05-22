package hu.u_szeged.inf.fog.simulator.rl;

import java.util.Objects;

public class State {
    public static final int taskGroups = 2;

    public static final int taskLimit = 20; // <20 or >= 20

    private final int taskBucket;

    /**
     * Initializes the State
     * @param type What type of initialization; Normal groups the task, Initialize interprets it as the group
     * @param task Either interprets it as the total amount of tasks, or the group ID
     */
    public State(StateType type, int task) {
        if (type == StateType.NORMAL) {
            this.taskBucket = task < taskLimit ? 0 : 1;
        } else {
            this.taskBucket = task;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State other)) return false;

        return taskBucket == other.taskBucket;
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskBucket);
    }

    @Override
    public String toString() {
        return "State{taskBucket:" + taskBucket + "}";
    }
}
