package hu.u_szeged.inf.fog.simulator.energyprovider;

public abstract class EnergySource {

    boolean renewable;

    EnergySource(boolean renewable) {
        this.renewable = renewable;
    }

    abstract float Production(long time, long frequency);

}
