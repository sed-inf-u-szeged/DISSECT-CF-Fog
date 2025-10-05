package hu.u_szeged.inf.fog.simulator.agent.urbannoise;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.agent.Capacity.Utilisation;
import hu.u_szeged.inf.fog.simulator.agent.SwarmAgent;

public class RemoteServer {
    
    SwarmAgent sa;
    
    Utilisation util;
    
    public PhysicalMachine pm;
    
    public RemoteServer(SwarmAgent sa, Utilisation util) {
        this.sa = sa;
        this.util = util;
        this.pm = util.vm.getResourceAllocation().getHost();
        sa.registerComponent(this);
    }
}
