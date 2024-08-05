package hu.u_szeged.inf.fog.simulator.agent;

public class Pair {
	ResourceAgent ra;
	
	Constraint constraint;
	
	public Pair(ResourceAgent ra, Constraint constraint) {
		this.constraint = constraint;
		this.ra = ra;
	}

	@Override
	public String toString() {
		return "Pair: " + ra + "-" + constraint;
	}
	
}
