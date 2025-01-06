package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.io.*;
import java.sql.Time;
import java.util.List;

public class Charge extends Timed {

    Provider provider;

    Charge(long freq, Provider provider) {
        subscribe(freq);
        this.provider = provider;
    }

    @Override
    public void tick(long fires) {
        if (Timed.getFireCount() > 3_600_000 * 24) {
            unsub();
        }

        if (getTotalProduction() > 0) {
            try {
                new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
                PrintStream out = new PrintStream(
                        new FileOutputStream("src/main/java/hu/u_szeged/inf/fog/simulator/energyprovider/output.txt", true), true);
                System.setOut(out);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            System.out.print("Current energy: " + provider.renewableBattery.getBatteryLevel() + " Wh");
            System.out.print("  -------  Added energy: " + getTotalProduction() + " Wh");
            System.out.println("  -------  Energy after charge: " + chargeUp() + " Wh");
        }


    }

    public void unsub() {
        unsubscribe();
    }

    private float chargeUp() {
        return this.provider.renewableBattery.chargeUp(getTotalProduction());
    }

    private float getTotalProduction() {
        float sum = 0;
        for (EnergySource source : this.provider.renewableSources) {
            sum += source.Production(Timed.getFireCount(), getFrequency());
        }
        return sum;
    }

}
