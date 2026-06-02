package hu.u_szeged.inf.fog.simulator.agent.dt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.dt.DigitalTwinRequest.ResourceNode;
import hu.u_szeged.inf.fog.simulator.agent.dt.DigitalTwinRequest.Component;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import java.util.HashMap;
import java.util.Map;

public class SimulationBuilder {

    public static void build(DigitalTwinRequest request, NoiseCsvData noiseData) {
        Map<String, Integer> sharedLatencyMap = new HashMap<>();

        for (ResourceNode resourceNode : request.resources) {
            ComputingAppliance node = new ComputingAppliance(
                    Config.createNode(resourceNode.nodeId, resourceNode.cpu, resourceNode.memoryMb * ScenarioBase.MB_IN_BYTE,
                            resourceNode.storageGb * ScenarioBase.GB_IN_BYTE,resourceNode.minPower, resourceNode.idlePower, resourceNode.maxPower,
                            resourceNode.bandwidthMbps * ScenarioBase.MBPS_TO_BPMS, resourceNode.latencyMs, sharedLatencyMap),
                    null, resourceNode.location, resourceNode.provider, true);
        }
        HashMap<Component, VirtualMachine> componentVmMap = new HashMap<>();
        for (Component component : request.application.components) {
            AlterableResourceConstraints arc = new AlterableResourceConstraints(component.cpuRequest, 1, component.memoryRequestMb * ScenarioBase.MB_IN_BYTE);
            VirtualAppliance va = new VirtualAppliance(component.componentId + "-va", 0, 0, false, component.properties.imageSizeBytes);

            ComputingAppliance ca = ComputingAppliance.allComputingAppliances.get(component.mappedNode);
            ca.iaas.repositories.get(0).registerObject(va);
            try {
                VirtualMachine vm = ca.iaas.requestVM(va, arc, ca.iaas.repositories.get(0), 1)[0];
                componentVmMap.put(component, vm);
            } catch (VMManager.VMManagementException e) {
                throw new RuntimeException(e);
            }
        }
        Timed.simulateUntilLastEvent();
        Timed.resetTimed();

        AgentApplication app = new AgentApplication();
        app.name = request.application.applicationId;
        GreedyNoiseSwarmAgent sa = new GreedyNoiseSwarmAgent(app);
        for (Component component : request.application.components) {
            Utilisation util = new Utilisation();
            util.vm = componentVmMap.get(component);
            util.component = new AgentApplication.Component();
            util.component.id = component.componentId;

            if (component.componentId.contains("sensor")){
                new NoiseSensor(sa, util, component.properties.inside, component.properties.sun);
            } else {
                new RemoteServer(sa, util);
            }
        }



        /*
        for(ComputingAppliance ca : ComputingAppliance.allComputingAppliances.values()){
            System.out.println(ca);
            for(VirtualMachine vm : ca.iaas.listVMs()){
                System.out.println("  " + vm);
            }
        }
        */
    }
}
