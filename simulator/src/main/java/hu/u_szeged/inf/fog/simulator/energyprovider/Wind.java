package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.Random;

public class Wind extends EnergySource{

    int turbines;
    float output;
    float lastCapacityFactor;
    int currentHour;

    public Wind(int turbines, float output) {
        super(true);
        this.turbines = turbines;
        this.output = output;
        Random rnd = new Random();
        this.lastCapacityFactor = (float) (rnd.nextInt(20) + 10) / 100;
    }

    @Override
    public float Production(long time, long frequency) {
        float frequencyFactor = (float) frequency / 3_600_000;
        return turbines * output * CalculateOutputFactor() * frequencyFactor;
    }

    float CalculateOutputFactor() {
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
