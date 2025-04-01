package hu.u_szeged.inf.fog.simulator.energyprovider.tests;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.energyprovider.*;

import java.util.ArrayList;

public class SolarChargeTest extends Timed {

    Provider provider;

    SolarChargeTest() {
        subscribe(3_500_000);

        Solar solar = new Solar(1,500);
        Battery battery = new Battery(10_000,500,0,100);
        FossilSource fossilSource = new FossilSource(1000);

        this.provider = new Provider(battery,new ArrayList<EnergySource>(),fossilSource,3_600_000,3_600_001, 1,1, 75);

        provider.addEnergySource(solar);

        Timed.simulateUntilLastEvent();
    }

    public static void main(String[] args) {

        SolarChargeTest test = new SolarChargeTest();

    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() > 3_600_000 * 48) {
            provider.stopProcessing();
            unsubscribe();
        }
    }
}
