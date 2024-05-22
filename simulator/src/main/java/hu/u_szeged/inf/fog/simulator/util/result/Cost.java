package hu.u_szeged.inf.fog.simulator.util.result;

public class Cost {

    private double totalCostInEuro;
    private String IBM;
    private String AWS;
    private String azure;
    
    public Cost(double totalCostInEuro, String IBM, String AWS, String azure) {
        this.totalCostInEuro = totalCostInEuro;
        this.IBM = IBM;
        this.AWS = AWS;
        this.azure = azure;
    }
}
