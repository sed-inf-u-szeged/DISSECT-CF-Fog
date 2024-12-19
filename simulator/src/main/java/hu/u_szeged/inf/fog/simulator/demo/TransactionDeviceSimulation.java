package hu.u_szeged.inf.fog.simulator.demo;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;

import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.BlockValidator;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.DistributedLedger;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.Mempool;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.TransactionDevice;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.crypto_strategy.RSAStrategy;
import hu.u_szeged.inf.fog.simulator.iot.distributed_ledger.digest_strategy.SHA256DigestStrategy;

import java.util.*;

public class TransactionDeviceSimulation {

    public static void main(String[] args) throws VMManager.VMManagementException, NetworkNode.NetworkException {
        System.out.println("Starting Transaction Device Simulation...");

        // For now the read and write operations of transactions won't be modeled
        Mempool mempool = new Mempool();
        DistributedLedger distributedLedger = new DistributedLedger(20, 1000,2, new RSAStrategy(), new SHA256DigestStrategy());

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
        PhysicalMachine pm = new PhysicalMachine(8, 1, 8589934592L, repo, 10_000, 10_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));

        pm.turnon();

        Timed.simulateUntilLastEvent();
        System.out.println("Time: " + Timed.getFireCount() + " PM-state: " + pm.getState());

        VirtualAppliance va = new VirtualAppliance("va", 800, 0, false, 1_073_741_824L);
        repo.registerObject(va);

        AlterableResourceConstraints arc = new AlterableResourceConstraints(4, 1, 4_294_967_296L);
        VirtualMachine[] vm = pm.requestVM(va, arc, repo, 2);

        BlockValidator blockValidator = new BlockValidator(vm[0], mempool, 3000);



        List<TransactionDevice> deviceList = new ArrayList<>();

        long stopTime = 10 * 60 * 60 * 1000;
        long deviceFreq = 60 * 1000;
        long transactionSize = 100;
        int latency = 0;

        // Start Transaction Devices
        for (int i = 0; i < 1; i++) {
            EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> deviceTransitions =
                    PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);

            final Map<String, PowerState> cpuTransitions = deviceTransitions.get(PowerTransitionGenerator.PowerStateKind.host);
            final Map<String, PowerState> stTransitions = deviceTransitions.get(PowerTransitionGenerator.PowerStateKind.storage);
            final Map<String, PowerState> nwTransitions = deviceTransitions.get(PowerTransitionGenerator.PowerStateKind.network);

            Repository deviceRepo = new Repository(4_294_967_296L, "device-repo" + i, 3_250, 3_250, 3_250, new HashMap<>(), stTransitions, nwTransitions); // 26 Mbit/s
            PhysicalMachine localMachine = new PhysicalMachine(1, 0.001, 1_073_741_824L, deviceRepo, 0, 0, cpuTransitions);

            TransactionDevice device = new TransactionDevice(0, stopTime, transactionSize, deviceFreq, localMachine, latency, mempool);
            deviceList.add(device);
        }

        // Start the Simulation
        Timed.simulateUntilLastEvent();

        System.out.println("Simulation complete.");
    }
}

