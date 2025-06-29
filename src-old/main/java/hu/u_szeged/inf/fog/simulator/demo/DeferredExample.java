package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

public class DeferredExample extends DeferredEvent {

    public DeferredExample(long delay) {
        super(delay);
    }

    @Override
    protected void eventAction() {
        new TimedExample("TimedTest #2", 25);
    }

public static void main(String[] args) {
        new TimedExample("TimedTest #1", 100);
        new DeferredExample(200);

        Timed.simulateUntilLastEvent();
    }
}