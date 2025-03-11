package hu.u_szeged.inf.fog.simulator.energyprovider.tests;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.energyprovider.*;

import java.util.ArrayList;

public class WindChargeTest {

    public static void main(String[] args) {

        Wind wind1 = new Wind(1, 500);

        Battery battery1 = new Battery(500_000_000, 1500, 0, 100);

        Provider provider = new Provider(battery1, new ArrayList<>(),new FossilSource(2000),3_600_000);

        provider.addEnergySource(wind1);

        Timed.simulateUntilLastEvent();

    }

}
