package hu.u_szeged.inf.fog.simulator.demo;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.Capacity;
import hu.u_szeged.inf.fog.simulator.agent.Deployment;
import hu.u_szeged.inf.fog.simulator.agent.Submission;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.strategy.FirstFitAgentStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.util.EnergyDataCollector;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.agent.AgentApplicationReader;

public class AgentTest {

    public static void main(String[] args) throws NetworkException {
        
        SimLogger.setLogging(1, true);

        /** ranking config */
        //ResourceAgent.rankingScriptDir = "D:\\Documents\\swarm-deployment\\for_simulator";
        ResourceAgent.rankingScriptDir = "/home/markusa/Documents/SZTE/repos/swarm-deployment/for_simulator";
                
        // ResourceAgent.rankingMethodName = "random";
        // ResourceAgent.rankingMethodName = "rank_no_re";
         ResourceAgent.rankingMethodName = "rank_re_add";
        //ResourceAgent.rankingMethodName = "rank_re_mul";
        // ResourceAgent.rankingMethodName = "vote_wo_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability";
        // ResourceAgent.rankingMethodName = "vote_w_reliability_mul";
        
        /** applications */
        String appInputFile = ScenarioBase.resourcePath + "AGENT_examples" + File.separator + "app_input.json";
        String appInputFile2 = ScenarioBase.resourcePath + "AGENT_examples" + File.separator + "App-401656782.json";
        
        /** clouds */
        Map<String, Integer> latencyMap = new HashMap<>();        
        
        ComputingAppliance cloud1 = new ComputingAppliance(
                createCloud(1000, 1000 * 1_073_741_824L, 1000 * 1_073_741_824L, 125_000, 40, 200, 340, latencyMap), 
                "cloud1", 65, new GeoLocation(47.45, 19.04), "EU", "AWS");
        ComputingAppliance cloud2 = new ComputingAppliance(
                createCloud(1000, 1000 * 1_073_741_824L, 1000 * 1_073_741_824L, 187_500, 30, 220, 300, latencyMap),
                "cloud2", 76, new GeoLocation(48.85, 2.35), "EU", "Azure");        
        ComputingAppliance cloud3 = new ComputingAppliance(
                createCloud(1000, 1000 * 1_073_741_824L, 1000 * 1_073_741_824L, 250_000, 20, 240, 420, latencyMap),
                "cloud3", 95, new GeoLocation(40.71, -74.00), "US", "AWS");
        ComputingAppliance cloud4 = new ComputingAppliance(
                createCloud(1000, 1000 * 1_073_741_824L, 1000 * 1_073_741_824L, 225_000, 25, 190, 425, latencyMap),
                "cloud4", 89, new GeoLocation(40.71, -74.00), "US", "Azure");
        
        new EnergyDataCollector(cloud1.iaas, 60 * 1000);
        new EnergyDataCollector(cloud2.iaas, 60 * 1000);
        new EnergyDataCollector(cloud3.iaas, 60 * 1000);
        new EnergyDataCollector(cloud4.iaas, 60 * 1000);
        
        /** agents */
        VirtualAppliance resourceAgentVa = new VirtualAppliance("resourceAgentVa", 30_000, 0, false, 536_870_912L); 
        AlterableResourceConstraints resourceAgentArc = new AlterableResourceConstraints(1, 1, 536_870_912L);
        
        new ResourceAgent("Agent-1", 0.0117, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false), 
                new Capacity(cloud1, 40.0, 40 * 1_073_741_824L, 200 * 1_073_741_824L));
        
        new ResourceAgent("Agent-2", 0.0115, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud2, 40.0, 40 * 1_073_741_824L, 200 * 1_073_741_824L));

        new ResourceAgent("Agent-3", 0.0113, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(false),
                new Capacity(cloud3, 40.0, 40 * 1_073_741_824L, 200 * 1_073_741_824L));

        new ResourceAgent("Agent-4", 0.0111, resourceAgentVa, resourceAgentArc, new FirstFitAgentStrategy(true),
                new Capacity(cloud4, 40.0, 40 * 1_073_741_824L, 200 * 1_073_741_824L));

        /** Image service */
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(1, 1, 1, 1, 1);
        
        Deployment.registryService = new Repository(Long.MAX_VALUE, "Image_Service", 125_000, 125_000, 125_000, new HashMap<>(), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
        
        Deployment.setImageRegistry(Deployment.registryService, 200);

        /** submitting applications */
        AgentApplication app1 = AgentApplicationReader.readAgentApplications(appInputFile);
        AgentApplication app2 = AgentApplicationReader.readAgentApplications(appInputFile2);

        //new Submission(app1, 100, 50);
        new Submission(app2, 150, 0);
        
        Timed.simulateUntilLastEvent();
        
        /** results */
        SimLogger.logRes("\nSimulation completed.");
        
        SimLogger.logRes("\nCapacity usage: ");
        double totalCost = 0;
        for (ResourceAgent agent : ResourceAgent.resourceAgents) {
            double runtime = 0;
            for (Capacity cap : agent.capacities) {
                SimLogger.logRes("\t" + cap);
                for (Utilisation util : cap.utilisations) {
                   SimLogger.logRes("\t\t" + util);
                   runtime += (Timed.getFireCount() - util.initTime);
                }
            }
            totalCost += agent.hourlyPrice * (runtime / 1000 / 60 / 60);
        }

        SimLogger.logRes("\nSimulation time (min.): " + Timed.getFireCount() / 1000.0 / 60.0);
        SimLogger.logRes("Total price (EUR): " + totalCost);
        
        for (AgentApplication app : AgentApplication.agentApplications) {
            SimLogger.logRes(app.name + " deployment time (min.): " + app.deploymentTime / 1000 / 60 +
                    " Available offers: " + app.offers.size());
        }
        
        double totalEnergy = 0;
        for (EnergyDataCollector ec : EnergyDataCollector.energyCollectors) {
            totalEnergy += ec.energyConsumption / 1000 / 3_600_000;
        }
        SimLogger.logRes("Total energy (kWh): " + totalEnergy);
        
        /*
        for(ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            SimLogger.logRes(ca.name + ":");
            SimLogger.logRes("\t Contents:");
            for(StorageObject so : ca.iaas.repositories.get(0).contents()) {
                SimLogger.logRes("\t\t" + so);
            }
            SimLogger.logRes("\t VMs:");
            for(VirtualMachine vm : ca.iaas.listVMs()) {
                SimLogger.logRes("\t\t" + vm.toString());
            }
        }
        
        SimLogger.logRes("Image Registry's contents");
        for(StorageObject so : Submission.imageRegistry.contents()) {
            SimLogger.logRes("\t" + so);
        }
        */
    }
    
    private static IaaSService createCloud(double cpu, long memory, long storage, long bandwidth,
            double minpower, double idlepower, double maxpower, Map<String, Integer> latencyMap) {
        
        IaaSService iaas = null;
        
        try {
             iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);     
             final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                     PowerTransitionGenerator.generateTransitions(minpower, idlepower, maxpower, 10, 10);
             
             // PM
             Repository pmRepo1 = new Repository(storage, "localRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                     transitions.get(PowerTransitionGenerator.PowerStateKind.network));

             PhysicalMachine pm1 = new PhysicalMachine(cpu, 1, memory, pmRepo1, 60_000, 60_000, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.host));

             iaas.registerHost(pm1);
             
             // Repository
             Repository cloudRepo = new Repository(storage, "cloudRepo", bandwidth, bandwidth, bandwidth, latencyMap, 
                     transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                     transitions.get(PowerTransitionGenerator.PowerStateKind.network));
             
             iaas.registerRepository(cloudRepo);
             cloudRepo.addLatencies("localRepo", 20);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return iaas;
    }
}