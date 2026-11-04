package hu.u_szeged.inf.fog.simulator.so_smart;

/**
 * Represents the mobility service operators available
 * in the SO-SMART simulation.
 * <p>
 * Each operator provides a different type of transportation
 * service that can be used during a trip.
 */
public enum Operator {
    PUBLIC_TRANSPORT("Public Transport", 100),
    CAR_SHARING("Car Sharing", 250),
    BIKE_SHARING("Bike Sharing", 50);

    private final String name;
    private final double pricePerKm;

    /**
     * Creates a mobility service operator.
     *
     * @param name       the name of the operator
     * @param pricePerKm the cost per kilometer in HUF
     */
    Operator(String name, double pricePerKm) {
        this.name = name;
        this.pricePerKm = pricePerKm;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the cost per kilometer.
     *
     * @return the price per kilometer in HUF
     */
    public double getPricePerKm() {
        return pricePerKm;
    }

}
