package hu.u_szeged.inf.fog.simulator.agent;

public class Constraint {

    String name;
    
    int value;
    
    public Constraint(String name, int value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Constraint(" + name + "," + value + ")";
    }
}