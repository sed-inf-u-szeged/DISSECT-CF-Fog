package hu.u_szeged.inf.fog.simulator.energyprovider;

import java.util.Random;

public class Wind extends EnergySource{

    int turbines;
    float output;

    Wind(int turbines, float output) {
        super(true);
        this.turbines = turbines;
        this.output = output;
    }

    //TODO summer-winter simulation
    @Override
    float Production(long time, long frequency) {
        Random rand = new Random();
        float random = (float) (rand.nextInt(40) + 1) /100;
        return (turbines * output * ( (float) frequency / 3_600_000) * random);
    }

}
