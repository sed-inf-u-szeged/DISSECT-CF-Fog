package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.Random;

public class Solar extends EnergySource {

    int output;
    int panels;
    int currentDay = 1;
    float multiplier = 1;


    public Solar(int panels, int output) {
        this.output = output;
        this.panels = panels;
    }

    @Override
    public float production(long time, long frequency) {
        return output * panels *
               calculateSinecurveMultiplier(
                       this.getTime(time) )
                       * ( (float) frequency / 3_600_000);
    }


    float calculateSinecurveMultiplier(float x) {
        updateMultiplier();
        float value = (float) (this.multiplier * (Math.sin(0.28 * x + 4.22) ) );
        if (value >= 0) {
            return value;
        } else {
            return 0;
        }
    }

    int calculateDay() {
        int day = (int) Math.ceil((double) Timed.getFireCount() / 3_600_000 / 24);
        return day;
    }

    void updateMultiplier() {
        if (this.currentDay < calculateDay()) {
            this.currentDay = calculateDay();
            Random rand = new Random();
            this.multiplier = rand.nextFloat();
        }
    }

}


