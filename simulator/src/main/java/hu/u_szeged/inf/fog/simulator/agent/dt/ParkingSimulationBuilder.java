package hu.u_szeged.inf.fog.simulator.agent.dt;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingGateway;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.ParkingSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.parking.PlatformService;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.parking.ParkingTimeBasedSwarmAgent;

import java.util.*;

public class ParkingSimulationBuilder {

    public static void build(ParkingDigitalTwinRequest request, ParkingCsvData parkingData) {

        Config.PARKING_CONFIGURATION.put("cooldownFreq", request.metadata.scalingCooldownMs);
        Config.PARKING_CONFIGURATION.put("gatewayFreq", request.metadata.blePollingIntervalMs);
        Config.PARKING_CONFIGURATION.put("orchestrationEnabled", false); // TODO: !!!

        ParkingDigitalTwinRequest.ResourceNode platformNode =
                request.resources.stream()
                        .filter(r -> "cloud".equals(r.nodeType))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException("Parking platform resource not found"));

        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(0, 0, 0, 0, 0);

        Map<String, Integer> platformLatencyMap = new HashMap<>();

        long platformBandwidth = platformNode.networkBandwidthBytesPerMs;

        Repository platformRepo = new Repository(
                platformNode.storageGb * 1024L * 1024L * 1024L,
                "platformRepo",
                platformBandwidth,
                platformBandwidth,
                platformBandwidth,
                platformLatencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network)
        );

        try {
            platformRepo.setState(NetworkNode.State.RUNNING);
        } catch (NetworkNode.NetworkException e) {
            throw new RuntimeException(e);
        }

        PlatformService platformService = new PlatformService(platformRepo);

        for (ParkingDigitalTwinRequest.Component component : request.application.components) {

            if (!"parking-sensor".equals(component.properties.componentType)) {
                continue;
            }

            Map<String, Integer> nbiotLatencyMap = new HashMap<>();
            nbiotLatencyMap.put(
                    "platformRepo",
                    component.properties.nbiotNetworkLatencyMs
            );

            Map<String, Integer> bleLatencyMap = new HashMap<>();

            long nbiotBandwidth =
                    component.properties.nbiotNetworkBandwidthBytesPerMs;

            long bleBandwidth =
                    component.properties.bleNetworkBandwidthBytesPerMs;

            Repository nbiotRepo = new Repository(
                    8_388_608,
                    component.componentId + "-nbiotRepo",
                    nbiotBandwidth,
                    nbiotBandwidth,
                    nbiotBandwidth,
                    nbiotLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network)
            );

            Repository bleRepo = new Repository(
                    8_388_608,
                    component.componentId + "-bleRepo",
                    bleBandwidth,
                    bleBandwidth,
                    bleBandwidth,
                    bleLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network)
            );

            try {
                nbiotRepo.setState(NetworkNode.State.RUNNING);
                bleRepo.setState(NetworkNode.State.RUNNING);
            } catch (NetworkNode.NetworkException e) {
                throw new RuntimeException(e);
            }


            ParkingSensor.ParkingMode mode =
                    ParkingSensor.ParkingMode.valueOf(component.properties.mode);

            Deque<ParkingCsvData.ParkingEvent> events =
                    parkingData.eventsBySensor.get(component.componentId);

            if (events == null) {
                throw new IllegalArgumentException(
                        "No parking CSV column found for sensor: "
                                + component.componentId
                );
            }

            new ParkingSensor(
                    component.componentId,
                    platformService,
                    nbiotRepo,
                    bleRepo,
                    component.properties.batteryLevel,
                    mode,
                    ParkingSensor.ParkingZone.RESIDENTIAL,
                    events
            );
        }

        Map<String, ParkingGateway> gatewaysByResource = new HashMap<>();
        Map<String, Repository> gatewayReposByResource = new HashMap<>();

        for (ParkingDigitalTwinRequest.ResourceNode resource : request.resources) {
            if (!"edge".equals(resource.nodeType)) {
                continue;
            }

            Map<String, Integer> gatewayLatencyMap = new HashMap<>();
            gatewayLatencyMap.put(
                    "platformRepo",
                    resource.networkLatencyMs
            );

            long bleIncomingBandwidth = Math.round(
                    request.application.components.stream()
                            .filter(c -> "parking-sensor".equals(c.properties.componentType))
                            .filter(c -> resource.nodeId.equals(c.assignedResource))
                            .mapToLong(c -> c.properties.bleNetworkBandwidthBytesPerMs)
                            .average()
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "No parking sensor assigned to gateway: " + resource.nodeId
                                    )
                            )
            );

            Repository gatewayRepo = new Repository(
                    resource.storageGb * 1024L * 1024L * 1024L,
                    "gatewayRepo-" + resource.nodeId,
                    bleIncomingBandwidth,
                    resource.networkBandwidthBytesPerMs,
                    resource.networkBandwidthBytesPerMs,
                    gatewayLatencyMap,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network)
            );

            try {
                gatewayRepo.setState(NetworkNode.State.RUNNING);
            } catch (NetworkNode.NetworkException e) {
                throw new RuntimeException(e);
            }

            ParkingGateway gateway = new ParkingGateway(
                    resource.nodeId,
                    gatewayRepo,
                    platformService,
                    new ArrayList<>()
            );

            gatewaysByResource.put(resource.nodeId, gateway);
            gatewayReposByResource.put(resource.nodeId, gatewayRepo);
        }
        for (ParkingDigitalTwinRequest.Component component : request.application.components) {
            if (!"parking-sensor".equals(component.properties.componentType)) {
                continue;
            }

            ParkingGateway gateway = gatewaysByResource.get(component.assignedResource);
            Repository gatewayRepo = gatewayReposByResource.get(component.assignedResource);

            if (gateway == null || gatewayRepo == null) {
                throw new IllegalArgumentException(
                        "Gateway not found for parking sensor "
                                + component.componentId
                                + ": "
                                + component.assignedResource
                );
            }
            ParkingSensor sensor = ParkingSensor.allParkingSensors.stream()
                    .filter(s -> s.id.equals(component.componentId))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Parking sensor not found: " + component.componentId
                            )
                    );

            sensor.bleRepository.getLatencies().put(
                    gatewayRepo.getName(),
                    component.properties.bleNetworkLatencyMs
            );

            gateway.addSensor(sensor);
        }

        if (request.operations != null) {
            for (ParkingDigitalTwinRequest.Operation operation : request.operations) {
                if (!"sensor_mode_reconfiguration".equals(operation.type)) {
                    continue;
                }

                ParkingSensor sensor = ParkingSensor.allParkingSensors.stream()
                        .filter(s -> s.id.equals(operation.componentId))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Parking sensor not found: " + operation.componentId
                                )
                        );

                sensor.mode = ParkingSensor.ParkingMode.valueOf(operation.targetMode);
            }
        }

        AgentApplication app = new AgentApplication();
        app.name = request.application.applicationId;

        ParkingTimeBasedSwarmAgent sa = new ParkingTimeBasedSwarmAgent(app);
        sa.observedAppComponents.addAll(ParkingSensor.allParkingSensors);
        sa.start((long) Config.PARKING_CONFIGURATION.get("cooldownFreq")
        );
    }
}