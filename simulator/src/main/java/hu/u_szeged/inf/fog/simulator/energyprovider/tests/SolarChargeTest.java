package hu.u_szeged.inf.fog.simulator.energyprovider.tests;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.energyprovider.Battery;
import hu.u_szeged.inf.fog.simulator.energyprovider.FossilSource;
import hu.u_szeged.inf.fog.simulator.energyprovider.Provider;
import hu.u_szeged.inf.fog.simulator.energyprovider.Solar;

import java.util.ArrayList;

public class SolarChargeTest {

    public static void main(String[] args) {

        Solar solar1 = new Solar(3, 500);

        Battery battery1 = new Battery(500_000_000, 1500, 0, 100);

        Provider provider = new Provider(battery1, new ArrayList<>(),new FossilSource(2000),3_600_000);

        provider.addEnergySource(solar1);

        Timed.simulateUntilLastEvent();

    }

}
