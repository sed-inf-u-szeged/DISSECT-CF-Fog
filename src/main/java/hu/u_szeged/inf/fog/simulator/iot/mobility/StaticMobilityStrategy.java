package hu.u_szeged.inf.fog.simulator.iot.mobility;

public class StaticMobilityStrategy extends MobilityStrategy {

    public StaticMobilityStrategy(GeoLocation startPosition) {
        this.startPosition = startPosition;
        this.currentPosition = startPosition;
    }

    @Override
    public GeoLocation move(long freq) {
        return this.startPosition;
    }
}