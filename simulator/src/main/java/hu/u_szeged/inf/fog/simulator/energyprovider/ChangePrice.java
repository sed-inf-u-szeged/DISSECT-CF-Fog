package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;

import java.io.*;

public class ChangePrice extends Timed {

    Provider provider;
    int maxChange;

    /**
     * A recurring timed event Class that changes the renewable energy price of a Provider
     * @param provider          the provider that we want its price changes
     * @param maxChange         the maximum ammount that the price changes by in percentages compared to the base price
     */

    public ChangePrice(Provider provider, int maxChange) {
        this.provider = provider;
        subscribe(this.provider.priceFreq);

        if (maxChange < 0) {
            this.maxChange = 0;
        }
        else if (maxChange > 75) {
            this.maxChange = 75;
        }
        else {
            this.maxChange = maxChange;
        }

    }

    @Override
    public void tick(long fires) {
        try {
            new File(new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath());
            PrintStream out = new PrintStream(
                    new FileOutputStream(ScenarioBase.resultDirectory +"/output.txt", true), true);
            System.setOut(out);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        System.out.print("Price before change: " + this.provider.renewablePrice);
        setEnergyPrice();
        System.out.println("  -------  Price after change: " + this.provider.renewablePrice);
    }

    /**
     * Sets the energy price according to the Battery level
     */

    private void setEnergyPrice() {
        this.provider.renewablePrice = calculateEnergyPrice();
    }

    /**
     * Calculates the energy price according to the Battery level
     * @return  The calculated price
     */

    public float calculateEnergyPrice() {
        double multiplier = this.maxChange / 100.0;
        return (float) (this.provider.renewableBasePrice * (1 + multiplier * ((50 - this.provider.renewableBattery.getBatteryPercentage()) / 50.0)));
    }

    /**
     * Stops the timed event
     * @return Whether stopping was succesful or not
     */

    public boolean stop() {
        return unsubscribe();
    }
}
