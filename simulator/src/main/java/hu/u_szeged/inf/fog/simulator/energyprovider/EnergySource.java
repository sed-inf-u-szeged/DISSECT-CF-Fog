package hu.u_szeged.inf.fog.simulator.energyprovider;

import static java.lang.Math.round;

public abstract class EnergySource {

    boolean renewable;

    EnergySource(boolean renewable) {
        this.renewable = renewable;
    }

    protected int getTime(long timePassed) {

        int hours = round( (float) timePassed / 3_600_000 );
        double hoursPastDay = Math.floor( (double) timePassed / 86_400_000 ) * 24;

        if ((int) (hours - hoursPastDay) < 18) {
            return (int) (hours - hoursPastDay) + 6;
        }
        return (int) (hours - hoursPastDay);
    }

    abstract float Production(long time, long frequency);

}
