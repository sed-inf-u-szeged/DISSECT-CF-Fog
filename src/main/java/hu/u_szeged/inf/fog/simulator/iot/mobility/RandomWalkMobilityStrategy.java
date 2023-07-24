package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

public class RandomWalkMobilityStrategy extends MobilityStrategy {

    public final double radius;

    final double speedMax;

    public RandomWalkMobilityStrategy(GeoLocation startPosition, double speedMin, double speedMax, double radius) {
        this.currentPosition = new GeoLocation(startPosition.latitude, startPosition.longitude);
        this.startPosition = startPosition;
        this.radius = radius;
        this.speed = speedMin;
        this.speedMax = speedMax;

    }

    @Override
    public GeoLocation move(Device device, long freq) {
        double direction = Math.toRadians((SeedSyncer.centralRnd.nextDouble() * 360));

        double lat1 = Math.toRadians(currentPosition.latitude);
        double lon1 = Math.toRadians(currentPosition.longitude);

        double distance = distance(freq);

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
                SmartDevice.stuckData+=device.calculateCurrentStuckData();
            }

        }

        currentPosition.latitude = lat2;
        currentPosition.longitude = lon2;

        return currentPosition;
    }

    private double distance(long freq) {
        return freq * (SeedSyncer.centralRnd.nextDouble() * (speedMax - speed) + speed);
    }

}
