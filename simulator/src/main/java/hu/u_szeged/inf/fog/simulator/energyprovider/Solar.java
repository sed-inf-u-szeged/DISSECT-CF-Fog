package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.*;
import hu.u_szeged.inf.fog.simulator.demo.simple.TimedExample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.round;

public class Solar extends EnergySource{

    int output;
    int panels;
    int currentDay = 1;
    float multiplier = 1;


    public Solar(int panels, int output) {
        super(true);
        this.output = output;
        this.panels = panels;
    }

    @Override
    public float Production(long time, long frequency) {
        return output * panels * calculateSineCurveMultipier(this.getTime(time)) * ( (float) frequency / 3_600_000);
    }

    //TODO simulate random days with less sun
    public float calculateSineCurveMultipier(double x) {
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


