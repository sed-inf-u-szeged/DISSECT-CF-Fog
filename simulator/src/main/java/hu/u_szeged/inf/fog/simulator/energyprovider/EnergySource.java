package hu.u_szeged.inf.fog.simulator.energyprovider;

public abstract class EnergySource {

    protected float getTime(long timePassed) {

        float hours =  (float) timePassed / 3_600_000;
        double hoursPastDay = Math.floor((double) timePassed / 86_400_000) * 24;

        return (float) (hours - hoursPastDay);
    }

    abstract float production(long time, long frequency);

}
