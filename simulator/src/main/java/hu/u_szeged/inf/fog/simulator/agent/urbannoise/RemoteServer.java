package hu.u_szeged.inf.fog.simulator.agent.urbannoise;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;

public class RemoteServer {
    
    SwarmAgent sa;
    
    Utilisation util;
    
    public PhysicalMachine pm;
    
    public String appName;
    
    public RemoteServer(SwarmAgent sa, Utilisation util, String appName) {
        this.sa = sa;
        this.util = util;
        this.pm = util.vm.getResourceAllocation().getHost();
        sa.registerComponent(this);
        this.appName = appName;
        this.pm.localDisk.registerObject(new StorageObject(appName, 0, false));
    }
}
