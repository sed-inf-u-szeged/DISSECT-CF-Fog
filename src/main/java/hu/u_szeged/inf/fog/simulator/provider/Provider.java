package hu.u_szeged.inf.fog.simulator.provider;

import java.util.ArrayList;

public abstract class Provider {
	
	public static ArrayList<Provider> providers = new ArrayList<>();
	
	public String name;
	
	double cost;

	public abstract double calculate();


}
