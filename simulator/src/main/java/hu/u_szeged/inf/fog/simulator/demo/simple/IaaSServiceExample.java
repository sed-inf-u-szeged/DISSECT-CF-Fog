package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class IaaSServiceExample {
    public static void main(String[] args) {
        try {
            
            IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
            
            // machines
            final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                    PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
            
            Repository pmRepo1 = new Repository(107_374_182_400L, "pmRepo1", 12_500, 12_500, 12_500, new HashMap<>(), 
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm1 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo1, 10_000, 10_000, 
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host));
            
            Repository pmRepo2 = new Repository(107_374_182_400L, "pmRepo2", 12_500, 12_500, 12_500, new HashMap<>(), 
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm2 = new PhysicalMachine(8, 1, 8_589_934_592L, pmRepo2, 10_000, 10_000, 
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host));
            
            iaas.registerHost(pm1);
            iaas.registerHost(pm2);
            
            // repositories
            Repository cloudRepo = new Repository(107_374_182_400L, "cloudRepo", 12_500, 12_500, 12_500, new HashMap<>(), 
                    transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            
            iaas.registerRepository(cloudRepo);
            
            cloudRepo.addLatencies("pmRepo1", 100);
            cloudRepo.addLatencies("pmRepo2", 125);

            // VMs
            VirtualAppliance va = new VirtualAppliance("ubuntu", 1000, 0, false, 1_073_741_824L);
            
            AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 1, 4_294_967_296L);
            
            iaas.repositories.get(0).registerObject(va);
            
            iaas.requestVM(va, arc, iaas.repositories.get(0), 3);

            Timed.simulateUntilLastEvent();

            // logging
            System.out.println("Time: " + Timed.getFireCount());
            
            for(PhysicalMachine pm : iaas.machines) {
                System.out.println(pm);
                for(VirtualMachine vm : pm.listVMs()) {
                    System.out.println("\t" + vm);
                }
                for(StorageObject content : pm.localDisk.contents()) {
                    System.out.println("\t" + content);
                }
            }
            for(Repository r : iaas.repositories) {
                System.out.println(r);
                for(StorageObject content : r.contents()) {
                    System.out.println("\t" + content);
                }
            }
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | VMManagementException e) {
            e.printStackTrace();
        }
    }
}