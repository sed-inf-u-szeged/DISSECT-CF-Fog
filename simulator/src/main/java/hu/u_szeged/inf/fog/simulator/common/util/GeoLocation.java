package hu.u_szeged.inf.fog.simulator.common.util;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

/**
 * Represents a geographical location defined by latitude and longitude.
 */
public class GeoLocation {

    private static final double EARTH_RADIUS_KM = 6378.137;

    public double latitude;

    public double longitude;

    /**
     * Constructs a new geographical location with the specified coordinates.
     *
     * @param latitude  latitude in degrees (-90 to +90)
     * @param longitude longitude in degrees (-180 to +180)
     */
    public GeoLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Calculates the distance between this location and another location
     * using the Haversine formula.
     *
     * @param other the other geographical location
     * @return distance between the two locations in meters
     */
    public double calculateDistance(GeoLocation other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;

        return distanceKm * 1000.0;
    }

    /**
     * Calculates the initial bearing (angle) from this location
     * to another location. The bearing is measured clockwise from 
     * geographic north and returned in degrees in the range [0, 360).
     *
     * @param other the destination geographical location
     * @return bearing angle in degrees
     */
    public double angle(GeoLocation other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    /**
     * Calculates the next geographical location reached by moving
     * a given distance from this location in a specified direction.
     *
     * @param distance distance to travel in meters
     * @param angle    bearing in degrees (clockwise from north)
     * @return the resulting geographical location
     */
    public GeoLocation nextLocation(double distance, double angle) {
        double radiusMeters = EARTH_RADIUS_KM * 1000.0;

        double lat1 = Math.toRadians(latitude);
        double lon1 = Math.toRadians(longitude);
        double bearing = Math.toRadians(angle);

        double lat2 = Math.asin(
                Math.sin(lat1) * Math.cos(distance / radiusMeters)
                        + Math.cos(lat1) * Math.sin(distance / radiusMeters) * Math.cos(bearing)
        );

        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(distance / radiusMeters) * Math.cos(lat1),
                Math.cos(distance / radiusMeters) - Math.sin(lat1) * Math.sin(lat2)
        );

        return new GeoLocation(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
    
    /**
     * Generates a random geographical location.
     */
    public static GeoLocation generateRandomGeoLocation() {
        double latitude = -90.0 + (90.0 - (-90.0)) * SeedSyncer.centralRnd.nextDouble();
        double longitude = -180.0 + (180.0 - (-180.0)) * SeedSyncer.centralRnd.nextDouble();
        return new GeoLocation(latitude, longitude);
    }
    
    @Override
    public String toString() {
        return "GeoLocation [latitude=" + latitude + ", longitude=" + longitude + "]";
    }
}