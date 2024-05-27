package hu.u_szeged.inf.fog.simulator.provider;

import java.util.ArrayList;

/**
 * This is an abstract class to represent an IoT provider service.
 * The concrete provider implementation has to override the calculate
 * method to determine the IoT-side cost of the provider.
 */
public abstract class Provider {
    
    /**
     * A list containing all instances of providers.
     */
    public static ArrayList<Provider> allProviders = new ArrayList<>();

    /**
     * The name of the provider.
     */
    public String name;

    /**
     * The actual cost of using the provider.
     */
    public double cost;

    /**
     * The method to be overridden, which defines the logic of the cost calculation.
     */
    public abstract double calculate();
}