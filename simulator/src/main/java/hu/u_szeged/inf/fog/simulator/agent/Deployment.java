package hu.u_szeged.inf.fog.simulator.agent;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.application.dummy.DummyServer;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.NoiseSensor;
import hu.u_szeged.inf.fog.simulator.agent.application.noise.RemoteServer;
import hu.u_szeged.inf.fog.simulator.agent.demo.Config;
import hu.u_szeged.inf.fog.simulator.agent.management.ForecastBasedSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.GreedyNoiseSwarmAgent;
import hu.u_szeged.inf.fog.simulator.agent.management.SwarmAgent;
import hu.u_szeged.inf.fog.simulator.common.node.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import org.apache.commons.lang3.tuple.Pair;

import static hu.u_szeged.inf.fog.simulator.agent.demo.Config.DUMMY_CONFIGURATION;
import static hu.u_szeged.inf.fog.simulator.agent.demo.Config.NOISE_CLASS_ONFIGURATION;

public class Deployment extends Timed {

    public static Repository registryService;

    Offer offer;

    AgentApplication app;

    Pair<ComputingAppliance, Utilisation> leadResource;

    public Deployment(Pair<ComputingAppliance, Utilisation> leadResource, Offer offer, AgentApplication app) {
        this.app = app;
        this.offer = offer;
        this.leadResource = leadResource;

        if (this.leadResource != null) {
            this.deployUtilisation(this.leadResource);
        } else {
            for (Pair<ComputingAppliance, Utilisation> resource : this.offer.utilisations) {
                if (!resource.getRight().leadResource && resource.getRight().utilisedCpu > 0) {
                    this.deployUtilisation(resource);
                }
            }
        }

        subscribe(1_000);
    }

    public static void setImageRegistry(Repository repository) {
        registryService = repository;
        try {
            registryService.setState(NetworkNode.State.RUNNING);
        } catch (NetworkException e) {
            SimLogger.logError("The registry service cannot be set to RUNNING state: " + e);
        }
    }

    private void deployUtilisation(Pair<ComputingAppliance, Utilisation> resource) {
        ComputingAppliance node = resource.getLeft();
        Utilisation utilisation = resource.getRight();

        AlterableResourceConstraints arc = new AlterableResourceConstraints(utilisation.utilisedCpu, 1, utilisation.utilisedMemory);
        VirtualAppliance va = (VirtualAppliance) registryService.lookup(utilisation.component.id);

        try {
            utilisation.vm = node.iaas.requestVM(va, arc, registryService, 1)[0];
            utilisation.initTime = Timed.getFireCount();
        } catch (VMManagementException e) {
            SimLogger.logError("VM deployment failed for " + app.name + ": " + e);
        }
    }

    private boolean checkRemainingDeployment() {
        for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
            if (util.getRight().utilisedCpu > 0 && !util.getRight().vm.getState().equals(VirtualMachine.State.RUNNING)) {
                return false;
            } else {
                util.getRight().setToAllocated();
            }
        }
        return true;
    }

    @Override
    public void tick(long fires) {
        if (this.leadResource != null && this.leadResource.getRight().vm.getState().equals(VirtualMachine.State.RUNNING)) {
            this.leadResource.getRight().setToAllocated();
            SimLogger.logRun("Lead Resource (" + this.leadResource.getRight().component.id + ") for "
                    + this.app.name + " started to run at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            new Deployment(null, this.offer, this.app);
            unsubscribe();
        } else if(this.leadResource == null && checkRemainingDeployment()) {
            SimLogger.logRun("Remaining resources for "
                    + this.app.name + " started to run at: " + Timed.getFireCount() / (double) ScenarioBase.MINUTE_IN_MILLISECONDS + " min.");
            app.deploymentTime = Timed.getFireCount() - app.deploymentTime;
            unsubscribe(); // The deployment is now done!
            deployActaulApplication();
        }
    }


    private void deployActaulApplication() {
        if (app.type.equals("dummy")) {
            SwarmAgent sa = new SwarmAgent(app);
            for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
                if (util.getRight().component.id.contains("server")) {
                    new DummyServer(sa, util.getRight());
                }else {
                    SimLogger.logError("This type of component is unfamiliar in the application");
                }
            }
        } else if (app.type.equals("noise-classification")) {
            GreedyNoiseSwarmAgent sa = null;
            if(Config.NOISE_CLASS_ONFIGURATION.get("swarmAgentType").equals("greedy")) {
                sa = new GreedyNoiseSwarmAgent(app);
            } else if (Config.NOISE_CLASS_ONFIGURATION.get("swarmAgentType").equals("forecast")){
                sa = new ForecastBasedSwarmAgent(app);
            } else {
                SimLogger.logError("This type of SA is unfamiliar in the application");
            }

            // TODO: PredictorBasedSwarmAgent sa = new PredictorBasedSwarmAgent(app, Config.PREDICTOR_SCRIPT);

            for (Pair<ComputingAppliance, Utilisation> util : this.offer.utilisations) {
                if (util.getRight().component.id.contains("noise-sensor")) {
                    new NoiseSensor(sa, util.getRight(), util.getRight().component.properties.inside, util.getRight().component.properties.sun);
                } else if (util.getRight().component.id.contains("remote-server")){
                    new RemoteServer(sa, util.getRight());
                }
                else {
                    SimLogger.logError("This type of component is unfamiliar in the application");
                }
            }
            sa.startNecesseryServices((int) Config.NOISE_CLASS_ONFIGURATION.get("minContainerCount"));
        } else {
            SimLogger.logError("Unknown application type to deploy: " + Config.APP_TYPE);
        }
    }
}