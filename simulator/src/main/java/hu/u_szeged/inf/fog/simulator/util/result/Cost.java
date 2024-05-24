package hu.u_szeged.inf.fog.simulator.util.result;

@SuppressWarnings("unused")
public class Cost {

    private double totalCostInEuro;
    private String ibm;
    private String aws;
    private String azure;
    
    public Cost(double totalCostInEuro, String ibm, String aws, String azure) {
        this.totalCostInEuro = totalCostInEuro;
        this.ibm = ibm;
        this.aws = aws;
        this.azure = azure;
    }
}
