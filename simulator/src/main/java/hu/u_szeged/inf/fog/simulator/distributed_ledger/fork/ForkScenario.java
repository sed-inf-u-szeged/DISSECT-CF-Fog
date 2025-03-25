package hu.u_szeged.inf.fog.simulator.distributed_ledger.fork;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.distributed_ledger.BlockValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public abstract class ForkScenario extends Timed {
    protected final String scenarioName;
    protected final boolean recurring;
    protected final double probability; // 0.0 to 1.0
    protected final long baseInterval; // interval for recurring forks
    protected final long variance;     // max jitter (+/- variance in ms)
    private static final Random random = SeedSyncer.centralRnd;

    public ForkScenario(String scenarioName, boolean recurring, double probability, long baseInterval, long variance) {
        this.scenarioName = scenarioName;
        this.recurring = recurring;
        this.probability = probability;
        this.baseInterval = baseInterval;
        this.variance = variance;
    }

    public boolean register(long frequency){
        return this.subscribe(frequency);
    }


    public abstract void executeScenario(List<BlockValidator> validators);

    @Override
    public void tick(long fires) {
        if (!recurring || random.nextDouble() < probability) {
            System.out.println("[Fork] Triggered: " + scenarioName + " at time " + fires);
            executeScenario(new ArrayList<>(BlockValidator.validators.values()));
        }

        if (recurring) {
            long jitter = (variance == 0) ? 0 : random.nextLong(-variance, variance + 1);
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
