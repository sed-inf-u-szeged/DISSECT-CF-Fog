package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.Random;

public class Wind extends EnergySource{

    int turbines;
    float output;
    float lastCapacityFactor;
    int currentHour;

    public Wind(int turbines, float output) {
        this.turbines = turbines;
        this.output = output;
        Random rnd = new Random();
        this.lastCapacityFactor = (float) (rnd.nextInt(20) + 10) / 100;
    }

    /**
     * Calls the built function that calculates the multiplier for this wind farm and scales it down to the frequency Charge is called
     * @param frequency The frequency of the Charge instance that belongs to this Wind class
     * @return The total production belonging to this wind farm
     */
    @Override
    public float production(long time, long frequency) {
        float frequencyFactor = (float) frequency / 3_600_000;
        return turbines * output * calculateOutputFactor() * frequencyFactor;
    }

    /**
     * Changes the production factor of this wind farm every hour
     * Changes it by [-0.2;0.2]
     * @return
     */
    float calculateOutputFactor() {
        if (this.currentHour < calculateHours()) {
            this.currentHour = calculateHours();
            Random rand = new Random();
            double variation = (rand.nextFloat() - 0.5) * 0.4;
            this.lastCapacityFactor = (float) Math.max(0.1, Math.min(0.9, this.lastCapacityFactor + variation));
        }
        return this.lastCapacityFactor;
    }

    int calculateHours() {
        int hours = (int) Math.floor((double) Timed.getFireCount() /3_600_000);
        return hours;
    }



}
