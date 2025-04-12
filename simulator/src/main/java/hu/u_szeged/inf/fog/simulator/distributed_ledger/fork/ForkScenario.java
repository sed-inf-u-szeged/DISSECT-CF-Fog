package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.Miner;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.List;
import java.util.Random;


/**
 * The abstract class {@code ForkScenario} serves as a base class for simulating fork scenarios
 * within a blockchain network. It handles timing, scheduling, and probabilistic execution
 * of various fork-related events.
 *
 * <p>Fork scenarios can be set to trigger either once or recur at defined intervals, with a
 * specified probability of execution at each tick. This class provides common infrastructure
 * and enforces the implementation of specific fork behaviors in derived classes.</p>
 *
 * @see Timed
 * @see Miner
 */
public abstract class ForkScenario extends Timed {
    protected final String scenarioName;
    /**
     * Indicates if the scenario is recurring or a single event.
     */
    protected final boolean recurring;
    /**
     * Probability (0.0 to 1.0) determining if the scenario triggers at each scheduled tick.
     */
    protected final double probability;
    /**
     * Base interval (ticks) between scenario occurrences if recurring.
     */
    protected final long baseInterval;
    /**
     * Maximum jitter variance (ticks) for interval scheduling.
     */
    protected final long variance;
    private static final Random random = SeedSyncer.centralRnd;

    /**
     * List of target miners affected by this scenario.
     */
    private List<Miner> targets = null;

    /**
     * Constructs a {@code ForkScenario} with specified parameters.
     *
     * @param scenarioName name of the fork scenario.
     * @param recurring    indicates if the scenario recurs periodically.
     * @param probability  probability of scenario execution at each tick.
     * @param baseInterval interval (in ticks) for recurring scenarios.
     * @param variance     maximum jitter (in ticks) applied to the interval.
     */
    public ForkScenario(String scenarioName, boolean recurring, double probability, long baseInterval, long variance) {
        this.scenarioName = scenarioName;
        this.recurring = recurring;
        this.probability = probability;
        this.baseInterval = baseInterval;
        this.variance = variance;
    }

    /**
     * Sets the target miners for the scenario.
     *
     * @param targets list of miners affected by the scenario.
     */
    public void setTargets(List<Miner> targets) {
        this.targets = targets;
    }

    /**
     * Registers the scenario.
     *
     * @param frequency interval frequency for initial registration.
     * @return {@code true} if successfully subscribed.
     */
    public boolean register(long frequency) {
        return this.subscribe(frequency);
    }

    /**
     * Unregisters the scenario.
     *
     * @return {@code true} if successfully unsubscribed.
     */
    public boolean unregister() {
        return this.unsubscribe();
    }

    /**
     * Abstract method to execute the specific scenario logic. Must be implemented by subclasses to define custom fork behaviors.
     *
     * @param miners list of miners to affect during scenario execution.
     */
    public abstract void executeScenario(List<Miner> miners);

    /**
     * Method invoked by the simulation timer to potentially trigger the scenario.
     * This method uses probabilistic execution and handles rescheduling based on configured intervals and variance.
     *
     * @param fires current simulation time tick.
     */
    @Override
    public void tick(long fires) {
        if (!recurring || random.nextDouble() < probability) {
            SimLogger.logRun("[Fork] Triggered: " + scenarioName + " at time " + fires);
            if (targets == null) {
                SimLogger.logError("[Fork] " + scenarioName + ": No targets found. Unsubscribing.");
                unsubscribe();
            }
            executeScenario(targets);
        }

        if (recurring) {
//            long jitter = (variance == 0) ? 0 : random.nextLong(-variance, variance + 1);
            long jitter = (variance == 0) ? 0 : random.nextInt((int) (2 * variance + 1)) - variance;
            long nextTrigger = fires + baseInterval + jitter;
            subscribe(nextTrigger);
        } else {
            unsubscribe();
        }
    }

    public String getScenarioName() {
        return scenarioName;
    }
}
