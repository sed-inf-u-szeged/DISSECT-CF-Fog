package hu.u_szeged.inf.fog.simulator.demo.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TransferExample extends ConsumptionEventAdapter {

    Repository from;
    Repository to;
    StorageObject so;
    long start;

    public TransferExample(Repository from, Repository to, StorageObject so) throws NetworkException {
        this.from = from;
        this.to = to;
        this.so = so;
        this.from.registerObject(so);
        this.from.requestContentDelivery(so.id, to, this);
        this.start = Timed.getFireCount();
    }

    @Override
    public void conComplete() {
        this.from.deregisterObject(this.so);
        System.out.println("Start: " + this.start + " from: " +
                    this.from.getName() + " to: " + this.to.getName() + " end: " +Timed.getFireCount());
    }

    public static void main(String[] args) throws NetworkException {
      
        int fileSize = 100;
        long storageSize = 500;
        long bandwidth = 500;

        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);

        HashMap<String, Integer> latencyMap = new HashMap<String, Integer>();

        Repository repo1 = new Repository(storageSize, "repo1", bandwidth, bandwidth, bandwidth, latencyMap, 
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        Repository repo2 = new Repository(storageSize, "repo2", bandwidth, bandwidth, bandwidth, latencyMap,
                transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        repo1.setState(NetworkNode.State.RUNNING);
        repo2.setState(NetworkNode.State.RUNNING);

        latencyMap.put("repo1",5);
        latencyMap.put("repo2",6);

        new TransferExample(repo1, repo2, new StorageObject("file1", fileSize, false));
        new TransferExample(repo2, repo1, new StorageObject("file2", fileSize, false));

        Timed.simulateUntilLastEvent();

        System.out.println("repo1: " + repo1.contents());
        System.out.println("repo2: " + repo2.contents());
    }
}