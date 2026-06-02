package hu.u_szeged.inf.fog.simulator.agent.dt;

import java.util.Deque;

public class InputValidator {

    public static void validate(
            DigitalTwinRequest request,
            NoiseCsvData noiseData) {

        if (request.metadata == null) {
            throw new RuntimeException("Missing metadata");
        }

        if (request.application == null) {
            throw new RuntimeException("Missing application");
        }

        if (request.application.components == null) {
            throw new RuntimeException("Missing application components");
        }

        validateNoiseSensors(request, noiseData);
        validatePredictionHorizon(request, noiseData);
    }

    private static void validatePredictionHorizon(
            DigitalTwinRequest request,
            NoiseCsvData noiseData) {

        long requiredMs =
                request.metadata.predictionHorizonMin * 60_000L;

        long availableMs =
                noiseData.maxSimulationTimeMs;

        if (availableMs < requiredMs) {
            throw new RuntimeException(
                    "CSV does not cover prediction horizon. Required: "
                            + requiredMs
                            + " ms, available: "
                            + availableMs
                            + " ms"
            );
        }
    }

    private static void validateNoiseSensors(
            DigitalTwinRequest request,
            NoiseCsvData noiseData) {

        for (DigitalTwinRequest.Component component
                : request.application.components) {

            String componentType =
                    component.properties != null
                            ? component.properties.componentType
                            : null;

            if (!"noise-sensor".equals(componentType)) {
                continue;
            }

            Deque<NoiseCsvData.SensorEvent> events =
                    noiseData.getEvents(component.componentId);

            if (events == null) {
                throw new RuntimeException(
                        "Missing CSV column for sensor component: "
                                + component.componentId
                );
            }

            if (events.isEmpty()) {
                throw new RuntimeException(
                        "No events found for sensor component: "
                                + component.componentId
                );
            }

            System.out.println(
                    "Validated sensor component "
                            + component.componentId
                            + " with "
                            + events.size()
                            + " events"
            );
        }
    }
}
