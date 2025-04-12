package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code ForkManager} class is responsible for managing and registering fork scenarios
 * within the blockchain simulation environment.
 *
 * <p>It maintains a list of {@code ForkScenario} instances and handles their scheduling
 * by invoking their {@code register} method. This allows centralized control over all
 * fork-related events within the simulation.</p>
 *
 * @see ForkScenario
 */
public class ForkManager {
    /**
     * List of registered fork scenarios.
     */
    private final List<ForkScenario> forkScenarios = new ArrayList<>();

    public void registerScenario(ForkScenario scenario, long scheduleTime) {
        forkScenarios.add(scenario);
        scenario.register(scheduleTime);
        SimLogger.logRun("Registered and scheduled: " + scenario.getScenarioName() + " at time " + scheduleTime);
    }

    /**
     * Returns the list of registered fork scenarios.
     *
     * @return a list of currently registered {@code ForkScenario} instances.
     */
    public List<ForkScenario> getForkScenarios() {
        return forkScenarios;
    }

    /**
     * Clears all registered fork scenarios by unregistering them and clearing the internal list.
     */
    public void clear() {
        for (ForkScenario scenario : forkScenarios) {
            unregisterScenario(scenario);
        }
    }

    /**
     * Registers and schedules multiple {@code ForkScenario} instances in bulk.
     *
     * @param scenarios a map of scenarios and their respective schedule times.
     */
    public void bulkRegisterScenarios(Map<ForkScenario, Long> scenarios) {
        for (Map.Entry<ForkScenario, Long> scenario : scenarios.entrySet()) {
            registerScenario(scenario.getKey(), scenario.getValue());
        }
    }

    /**
     * Unregisters and removes a specific {@code ForkScenario} from the manager.
     *
     * @param scenario the scenario to unregister.
     */
    public void unregisterScenario(ForkScenario scenario) {
        if (forkScenarios.remove(scenario)) {
            scenario.unregister();
            SimLogger.logRun("Unregistered scenario: " + scenario.getScenarioName());
        }
    }

    /**
     * Checks whether a given {@code ForkScenario} is currently registered.
     *
     * @param scenario the scenario to check.
     * @return {@code true} if registered, {@code false} otherwise.
     */
    public boolean isRegistered(ForkScenario scenario) {
        return forkScenarios.contains(scenario);
    }
}