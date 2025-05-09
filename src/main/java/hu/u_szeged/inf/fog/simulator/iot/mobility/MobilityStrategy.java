package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.u_szeged.inf.fog.simulator.iot.Device;

/**
 * The abstract class provides a template for creating different mobility 
 * strategies of moving devices.
 */
public abstract class MobilityStrategy {

    /**
     * The first position of the moving device.
     */
    public GeoLocation startPosition;

    /**
     * The actual position of the device.
     */
    public GeoLocation currentPosition;

    /**
     * The speed at which the device moves.
     */
    public double speed;

    /**
     * The method to be overridden, which determines the logic for calculating the
     * next position of the device.
     *
     * @param device the device that needs to be moved
     * @return the new geographical location of the device after moving
     */
    public abstract GeoLocation move(Device device);
}