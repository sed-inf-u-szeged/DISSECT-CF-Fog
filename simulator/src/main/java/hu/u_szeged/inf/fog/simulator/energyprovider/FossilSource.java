package hu.u_szeged.inf.fog.simulator.energyprovider;

public class FossilSource extends EnergySource{

    float production;

    FossilSource(float production) {
        super(false);
        this.production = production;
    }

    @Override
    float Production(long time, long frequency) {
        return this.production;
    }
}
