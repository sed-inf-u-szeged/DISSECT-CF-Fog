package hu.u_szeged.inf.fog.simulator.node;

import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

public class AgentComputingAppliance extends ComputingAppliance implements ResourceAgent {
    
    String continent;

    public AgentComputingAppliance(String file, String name, GeoLocation geoLocation, String continent)  {
        super(file, name, geoLocation, 0);
        this.continent = continent;
    }

    public static void setConnection(ComputingAppliance that, int latency) {
        for (ComputingAppliance ca : ComputingAppliance.getAllComputingAppliances()) {
            if (ca != that) {
                ca.iaas.repositories.get(0).addLatencies(that.iaas.repositories.get(0).getName(), latency);
            }
        }
    }

    @Override
    public void broadcast() {
        // TODO Auto-generated method stub
        
    }

    public void deploy() {
        // TODO Auto-generated method stub
        
    }
    
}
