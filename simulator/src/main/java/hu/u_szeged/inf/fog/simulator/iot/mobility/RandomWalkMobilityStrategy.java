package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * The class represents a mobility strategy where a device moves in a random 
 * direction within a specified radius from its start position. The speed is 
 * also chosen randomly.
 */
public class RandomWalkMobilityStrategy extends MobilityStrategy {

    /**
     * The maximum distance ('circle') the device can move from the start position.
     */
    public final double radius;

    /**
     * The maximum speed at which the device can move.
     */
    final double speedMax;

    /**
     * Constructs the strategy with the specified start position, speed range, and radius.
     *
     * @param startPosition the initial geographical location of the device
     * @param speedMin      the minimum speed at which the device moves
     * @param speedMax      the maximum speed at which the device moves
     * @param radius        the maximum distance ('circle') the device can move from the start position
     */
    public RandomWalkMobilityStrategy(GeoLocation startPosition, double speedMin, double speedMax, double radius) {
        this.currentPosition = new GeoLocation(startPosition.latitude, startPosition.longitude);
        this.startPosition = startPosition;
        this.radius = radius;
        this.speed = speedMin;
        this.speedMax = speedMax;

    }

    /**
     * Moves the device based on the random walk mobility strategy. If the new position
     * cannot be determined within 100 iterations as a function of random speed and direction,
     * the device is stopped. This is typically due to a small radius or too high speed.
     *
     * @param device the device to move
     * @return the new geographical location of the device after moving
     */
    @Override
    public GeoLocation move(Device device) {
        // TODO: the method can be simplified!
        double direction = Math.toRadians((SeedSyncer.centralRnd.nextDouble() * 360));

        double lat1 = Math.toRadians(currentPosition.latitude);
        double lon1 = Math.toRadians(currentPosition.longitude);

        double distance = distance(device.freq);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(distance / (GeoLocation.earthRadius * 1000))
                + Math.cos(lat1) * Math.sin(distance / (GeoLocation.earthRadius * 1000)) * Math.cos(direction));
        double lon2 = lon1 + Math.atan2(
                Math.sin(direction) * Math.sin(distance / (GeoLocation.earthRadius * 1000)) * Math.cos(lat1),
                Math.cos(distance / (GeoLocation.earthRadius * 1000)) - Math.sin(lat1) * Math.sin(lat2));

        lat2 = Math.toDegrees(lat2);
        lon2 = Math.toDegrees(lon2);

        double currentDistance = startPosition.calculateDistance(new GeoLocation(lat2, lon2));
        int i = 0;
        while (!(currentDistance <= radius)) {

            direction = Math.toRadians((SeedSyncer.centralRnd.nextDouble() * 360));

            lat1 = Math.toRadians(currentPosition.latitude);
            lon1 = Math.toRadians(currentPosition.longitude);

            lat2 = Math.asin(Math.sin(lat1) * Math.cos(distance / (GeoLocation.earthRadius * 1000))
                    + Math.cos(lat1) * Math.sin(distance / (GeoLocation.earthRadius * 1000)) * Math.cos(direction));
            lon2 = lon1 + Math.atan2(
                    Math.sin(direction) * Math.sin(distance / (GeoLocation.earthRadius * 1000)) * Math.cos(lat1),
                    Math.cos(distance / (GeoLocation.earthRadius * 1000)) - Math.sin(lat1) * Math.sin(lat2));

            lat2 = Math.toDegrees(lat2);
            lon2 = Math.toDegrees(lon2);

            currentDistance = startPosition.calculateDistance(new GeoLocation(lat2, lon2));
            if (++i > 100) {
                SimLogger.logRun(
                        "ERROR: After 100 iteration, the device's new position couldn't be determined because of"
                        + "the nodes' the small radius, or the device's high speed.");
                device.stopMeter();
                SmartDevice.stuckData += device.calculateStuckData();
            }
        }

        currentPosition.latitude = lat2;
        currentPosition.longitude = lon2;

        return currentPosition;
    }

    /**
     * Calculates and returns with the distance the device will travel based on the frequency.
     *
     * @param freq the frequency at which the movement is updated.
     */
    private double distance(long freq) {
        return freq * (SeedSyncer.centralRnd.nextDouble() * (speedMax - speed) + speed);
    }
}