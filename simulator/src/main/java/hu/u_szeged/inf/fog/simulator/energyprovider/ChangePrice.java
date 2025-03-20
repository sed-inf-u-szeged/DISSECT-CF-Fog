package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

public class ChangePrice extends Timed {

    Provider provider;
    int maxChange;

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
        setEnergyPrice();
        if (Timed.getFireCount() > 3_600_000 * 24) {
            unsubscribe();
        }
    }

    private void setEnergyPrice() {
        this.provider.renewablePrice = calculateEnergyPrice();
    }

    public float calculateEnergyPrice() {
        double multiplier = this.maxChange / 100.0;
        return (float) (this.provider.renewableBasePrice * (1 + multiplier * ((50 - this.provider.renewableBattery.getBatteryPercentage()) / 50.0)));
    }
}
