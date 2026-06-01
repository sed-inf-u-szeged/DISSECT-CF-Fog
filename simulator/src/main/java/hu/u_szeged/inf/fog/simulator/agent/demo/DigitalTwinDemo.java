package hu.u_szeged.inf.fog.simulator.agent.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.Sun;
import hu.u_szeged.inf.fog.simulator.agent.dt.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class DigitalTwinDemo {

    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.err.println("Usage: DigitalTwinDemo <input-json> <noise-csv>");
            //System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();

        //JsonNode input = mapper.readTree(new File(args[0]));
        //Path csvPath = Path.of(args[1]);
        Path csvPath = Path.of("/Users/markusa/Documents/git-repos/digital-twin/examples/noise-data.csv");
        NoiseCsvData noiseData = NoiseCsvData.load(csvPath);

        DigitalTwinRequest request =
                //mapper.readValue(Path.of(args[0]).toFile(), DigitalTwinRequest.class);
                mapper.readValue(
                        new File("/Users/markusa/Documents/git-repos/digital-twin/examples/dt-input.json"),
                        DigitalTwinRequest.class
                );

        InputValidator.validate(request, noiseData);

        switch (request.metadata.applicationType) {
            case "InnoRenew":
                SimulationBuilder.build(request, noiseData);
                break;
            default:
                System.err.println("Unknown digital twin type: " + request.metadata.applicationType);
                System.exit(1);
                return;
        }

        long startOffsetMs =
                noiseData.simulationStartTime.toLocalTime().toNanoOfDay() / 1_000_000L;
        Sun.init(6, 20, 13, 1.5, startOffsetMs);
        long starttime = System.nanoTime();
        Timed.simulateUntil(noiseData.maxSimulationTimeMs);
        long stoptime = System.nanoTime(); 

        /*
        System.out.println("Simulation start: "
                + noiseData.simulationStartTime);

        for (Map.Entry<String, Deque<SensorEvent>> entry
                : noiseData.eventsBySensor.entrySet()) {

            System.out.println("\n=== " + entry.getKey() + " ===");

            for (SensorEvent event : entry.getValue()) {
                System.out.println(
                        "t=" + event.simulationTimeMs()
                                + " ms -> "
                                + event.value()
                );
            }
        }
        */
    }
}