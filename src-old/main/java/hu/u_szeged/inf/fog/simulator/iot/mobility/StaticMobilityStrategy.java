package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.u_szeged.inf.fog.simulator.iot.Device;

/**
 * The StaticMobilityStrategy class represents a mobility strategy 
 * where a device remains stationary at a fixed geographical location.
 */
public class StaticMobilityStrategy extends MobilityStrategy {

    /**
     * Constructs a strategy with the specified start position.
     *
     * @param startPosition the geographical location where the device will remain stationary
     */
    public StaticMobilityStrategy(GeoLocation startPosition) {
        this.startPosition = startPosition;
        this.currentPosition = startPosition;
    }

    /**
     * It always returns null, indicating no movement.
     *
     * @param device the device that is not moving.
     */
    @Override
    public GeoLocation move(Device device) {
        return null;
    }
}