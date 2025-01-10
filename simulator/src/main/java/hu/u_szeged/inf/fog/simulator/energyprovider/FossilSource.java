package hu.u_szeged.inf.fog.simulator.energyprovider;

public class FossilSource extends EnergySource{

    FossilSource() {
        super(false);
    }

    @Override
    float Production(long time, long frequency) {
        return 99999999;
    }
}
