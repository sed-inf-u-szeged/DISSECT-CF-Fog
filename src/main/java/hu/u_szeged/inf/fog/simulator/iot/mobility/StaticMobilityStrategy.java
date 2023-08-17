package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.u_szeged.inf.fog.simulator.iot.Device;

public class StaticMobilityStrategy extends MobilityStrategy {

    public StaticMobilityStrategy(GeoLocation startPosition) {
        this.startPosition = startPosition;
        this.currentPosition = startPosition;
    }

    @Override
    public GeoLocation move(Device device, long freq) {
        return null;
    }
}