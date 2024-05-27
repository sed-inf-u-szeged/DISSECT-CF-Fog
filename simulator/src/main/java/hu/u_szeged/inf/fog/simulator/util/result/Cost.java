package hu.u_szeged.inf.fog.simulator.util.result;

/**
 * Helper class for saving the results of a simulation to the database used 
 * by DISSECT-CF-Fog-WebApp and the executor module.
 */
@SuppressWarnings("unused")
public class Cost {

    private double totalCost;
    private String ibm;
    private String aws;
    private String azure;
    
    /**
     * Constructs an object with the specified cost values.
     *
     * @param totalCost the total cost incurred
     * @param ibm       the cost breakdown for IBM Cloud
     * @param aws       the cost breakdown for AWS
     * @param azure     the cost breakdown for Azure
     */
    public Cost(double totalCost, String ibm, String aws, String azure) {
        this.totalCost = totalCost;
        this.ibm = ibm;
        this.aws = aws;
        this.azure = azure;
    }
}