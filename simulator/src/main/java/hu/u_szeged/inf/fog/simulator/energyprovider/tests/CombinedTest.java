package hu.u_szeged.inf.fog.simulator.energyprovider.tests;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.energyprovider.*;

import java.io.IOException;
import java.util.ArrayList;

public class CombinedTest extends Timed {

    Provider provider;

    CombinedTest() throws IOException {
        subscribe(3_500_000);

        Wind wind = new Wind(1,100);
        //Solar solar = new Solar(1,300);
        Battery battery = new Battery(40_000,0);
        FossilSource fossilSource = new FossilSource(1000);

        this.provider = new Provider("01", battery,new ArrayList<EnergySource>(),fossilSource,3_600_000, 1,1, 75,50);

        provider.addEnergySource(wind);
        //provider.addEnergySource(solar);

        ArrayList<Provider> providers = new ArrayList<>();

        providers.add(provider);

        Timed.simulateUntilLastEvent();
        RenewableVisualiser.visualiseStoredEnergy(providers);
        RenewableVisualiser.visualiseSolar(providers);
        RenewableVisualiser.visualiseWind(providers);
    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() > 3_600_000 * 96) {
            provider.stopProcessing();
            unsubscribe();
        }
    }

    public static void main(String[] args) throws IOException {
        new CombinedTest();
    }
}
