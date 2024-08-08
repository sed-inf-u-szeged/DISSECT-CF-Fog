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
        return " Pair:{" + ra + "-" + constraint + "} ";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Pair pair = (Pair) o;

        if (!ra.equals(pair.ra)) {
            return false;
        }
        return constraint.equals(pair.constraint);
    }

    @Override
    public int hashCode() {
        int result = ra.hashCode();
        result = 31 * result + constraint.hashCode();
        return result;
    }
}