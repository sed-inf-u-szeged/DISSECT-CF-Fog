package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;

public class Test {

    public static void main(String[] args) {

        Solar solar1 = new Solar(3, 500);
        Battery battery1 = new Battery(500_000_000, 1500, 0, 100);

        Provider provider = new Provider(battery1, new ArrayList<>(),3_600_000);

        provider.addEnergySource(solar1);

        new SingeDischarge(3_600_000*10,provider,10);

        Timed.simulateUntilLastEvent();

    }

}
