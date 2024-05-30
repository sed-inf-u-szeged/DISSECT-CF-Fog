package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

public class TimedExample extends Timed {

    String name;

    TimedExample(String id, long freq) {
        this.name = id;
        subscribe(freq);
    }

    @Override
    public void tick(long fires) {
        if(Timed.getFireCount() >= 300) {
            unsubscribe();
        }
        System.out.println(this.name + " - time: " + Timed.getFireCount());
    }

    public static void main(String[] args) {    
        new TimedExample("TimedTest #1", 100);  
        new TimedExample("TimedTest #2", 95);

        Timed.simulateUntilLastEvent();
    }
}