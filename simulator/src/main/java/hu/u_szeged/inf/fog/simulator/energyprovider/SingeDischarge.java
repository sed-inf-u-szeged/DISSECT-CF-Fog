package hu.u_szeged.inf.fog.simulator.energyprovider;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

public class SingeDischarge extends DeferredEvent {

    Provider provider;
    float ammount;

    public SingeDischarge(long delay, Provider provider, float ammount) {
        super(delay);
        this.provider = provider;
        this.ammount = ammount;
    }

    @Override
    protected void eventAction() {
        this.provider.renewableBattery.removeBatteryLevel(this.ammount);
        System.out.print("Ammount discharged: " + this.ammount);
        System.out.println("  -------  Energy after discharge: "+ this.provider.renewableBattery.batteryLevel);
    }
}
