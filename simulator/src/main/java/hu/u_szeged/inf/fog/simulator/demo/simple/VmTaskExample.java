package hu.u_szeged.inf.fog.simulator.demo.simple;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

public class VmTaskExample {

    public static void main(String[] args) throws NetworkException, VMManagementException {

        long storageSize = 107_374_182_400L; // 100 GB
        long bandwidth = 12_500; // 100 Mbps 
        
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        
        Repository repo = new Repository(storageSize, "repo", bandwidth, bandwidth, bandwidth, new HashMap<String, Integer>(), 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));
       
        repo.setState(NetworkNode.State.RUNNING);

        /** Creating a PM with 8 CPU cores, 8 GB RAM     **/
        /** 1 instruction/tick processing speed per core **/
        /** 10-10 seconds boot time and shutdown time    **/
        PhysicalMachine pm = new PhysicalMachine(8, 1, 8_589_934_592L, repo, 10_000, 10_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        pm.turnon();

        Timed.simulateUntilLastEvent();

        System.out.println("Time: " + Timed.getFireCount() + " PM-state: " + pm.getState());

        /** VM "image" file with deploy time of 800 instructions **/
        VirtualAppliance va = new VirtualAppliance("va", 800, 0, false, 1_073_741_824L);
        repo.registerObject(va);
        
        /** VM resource requirements **/
        AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 1, 4_294_967_296L);
        
        VirtualMachine vm = pm.requestVM(va, arc, repo, 1)[0];

        Timed.simulateUntilLastEvent();

        System.out.println("Time: " + Timed.getFireCount() + " PM-state: " + pm.getState() + " VM-state: " + vm.getState());

        /** Simulating 100_000 instructions to be processed by the VM **/
        vm.newComputeTask(100_000, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter() {

            @Override
            public void conComplete() {
                System.out.println("Time: " + Timed.getFireCount());
            }
        });

        Timed.simulateUntilLastEvent();
    }   
}