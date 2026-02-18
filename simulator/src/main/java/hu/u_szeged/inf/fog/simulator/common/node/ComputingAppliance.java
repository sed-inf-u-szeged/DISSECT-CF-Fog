package hu.u_szeged.inf.fog.simulator.common.node;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.u_szeged.inf.fog.simulator.common.util.GeoLocation;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a computational entity (e.g. cloud or edge node)
 * that provides infrastructure services.
 */
public class ComputingAppliance {

    /**
     * Global registry of all computing appliances created during the simulation.
     */
    public static final Map<String, ComputingAppliance> allComputingAppliances = new HashMap<>();
    
    /**
     * The underlying IaaS service provided by this computing appliance.
     */
    public IaaSService iaas;

    /**
     * Unique name of the computing appliance.
     */
    public final String name;

    /**
     * The service provider identifier for the computing appliance.
     */
    public String provider;
    
    /**
     * Physical or logical location for the computing appliance.
     */
    public String location;
    
    /**
     * Indicates whether this computing appliance is an edge node.
     */
    public boolean edge;
    
    /**
     * The physical position of the node.
     */
    public final GeoLocation geoLocation;
    
    /**
     * Constructs a new computational entity.
     *
     * @param iaas IaaS service associated with the appliance
     * @param location location of the appliance
     * @param provider provider identifier
     * @param edge true if the appliance is an edge node
     */
    public ComputingAppliance(IaaSService iaas, GeoLocation geoLocation, String location, String provider, boolean edge) {
        this.name = iaas.repositories.get(0).getName().contains("-") 
                ? iaas.repositories.get(0).getName().substring(0, iaas.repositories.get(0).getName().indexOf('-')) 
                : iaas.repositories.get(0).getName();
        this.iaas = iaas;
        this.geoLocation = geoLocation;
        this.location = location;
        this.provider = provider;
        this.edge = edge;
     
        if (allComputingAppliances.containsKey(name)) {
            SimLogger.logError("ComputingAppliance with name '" + name + "' already exists");
        }            
        allComputingAppliances.put(name, this);
    }

    @Override
    public String toString() {
        return "ComputingAppliance{" +
                "name='" + name + '\'' +
                ", provider='" + provider + '\'' +
                ", location='" + location + '\'' +
                ", edge=" + edge +
                '}';
    }
}