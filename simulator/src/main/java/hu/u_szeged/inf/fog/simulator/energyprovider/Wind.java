package hu.u_szeged.inf.fog.simulator.energyprovider;

public class Wind extends EnergySource{

    int turbines;
    float output;

    Wind(int turbines, float output) {
        super(true);
        this.turbines = turbines;
        this.output = output;
    }

    @Override
    float Production(long time, long frequency) {
        return 0;
    }
}
