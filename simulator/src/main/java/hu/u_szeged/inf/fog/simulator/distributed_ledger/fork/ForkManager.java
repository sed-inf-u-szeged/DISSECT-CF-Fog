package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import java.util.ArrayList;
import java.util.List;

public class ForkManager {
    private final List<ForkScenario> forkScenarios = new ArrayList<>();

    public void registerScenario(ForkScenario scenario, long scheduleTime) {
        forkScenarios.add(scenario);
        scenario.register(scheduleTime);
        System.out.println("Registered and scheduled: " + scenario.getScenarioName() + " at time " + scheduleTime);
    }
}