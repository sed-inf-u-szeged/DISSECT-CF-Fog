package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.u_szeged.inf.fog.simulator.iot.Device;

public abstract class MobilityStrategy {

    public GeoLocation startPosition;

    public GeoLocation currentPosition;

    public double speed;

    public abstract GeoLocation move(Device device, long freq);
}
