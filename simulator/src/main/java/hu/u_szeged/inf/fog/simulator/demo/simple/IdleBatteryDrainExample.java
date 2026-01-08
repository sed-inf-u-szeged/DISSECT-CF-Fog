package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.iot.Battery;

import hu.u_szeged.inf.fog.simulator.util.AgentVisualiser;

import java.io.File;
import java.nio.file.Path;



public class IdleBatteryDrainExample {
    public static void main(String[] args) {
        //drainRate jelenleg változásig mAh/h érték tehát kivonás előtt van egy 60-as osztó tehát a szimulációhoz nem túl reális "idle drain" értéket adtam meg mert amúgy órákat kell szimulálni
        Battery battery1 = new Battery("battery1", 3500, 4.4f, 0.065*20, 2 * 60 * 60 * 1000);
        Battery battery2 = new Battery("battery2", 4500, 4.4f, 0.065*20, 3 * 60 * 60 * 1000);

        //48 hour
        battery1.setStopTime(48 * 60 * 60 * 1000);
        battery2.setStopTime(48 * 60 * 60 * 1000);
        Timed.simulateUntilLastEvent();

        battery1.writeToFileConsumption(ScenarioBase.resultDirectory);
        battery2.writeToFileConsumption(ScenarioBase.resultDirectory);

        AgentVisualiser.visualise("batteryGraph", Path.of(ScenarioBase.resultDirectory + File.separator + "battery1.csv"), Path.of(ScenarioBase.resultDirectory + File.separator + "battery2.csv"));
    }
}
