package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.*;
import hu.u_szeged.inf.fog.simulator.demo.simple.TimedExample;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.round;

public class Solar extends EnergySource{

    int output;
    int panels;


    Solar(int panels, int output) {
        super(true);
        this.output = output;
        this.panels = panels;
    }

    @Override
    public float Production(long time, long frequency) {
        return output * panels * Multiplier(this.getTime(time)) * ( (float) frequency / 3_600_000);
    }



    //TODO do something more refined

    float Multiplier(int time){
        switch (time) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
                return 0;
            case 6:
            case 19:
                return 0.05F;
            case 7:
            case 18:
                return 0.1F;
            case 8:
            case 17:
                return 0.3F;
            case 9:
            case 16:
                return 0.5F;
            case 10:
            case 14:
                return 0.6F;
            case 11:
            case 13:
                return 0.9F;
            case 12:
                return 1.0F;
            case 15:
                return 0.7F;

        }
        return 0;
    }

    private int Converttime(String time){
        int hours = Integer.parseInt(time.substring(0, 2));
        int minutes = Integer.parseInt(time.substring(3, 5));
        int seconds = 0;
        if (time.length() > 5) {
            seconds = Integer.parseInt(time.substring(6, 8));
        }

        return round ( hours + ( (float) minutes / 60) + ( (float) seconds / 3600) );
    }

}


