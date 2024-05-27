package hu.u_szeged.inf.fog.simulator.iot.mobility;

/**
 * The class represents a geographic location specified by latitude and longitude coordinates.
 */
public class GeoLocation {

    /**
     * The approximate radius of the earth considered in the simulator (in km). 
     */
    static final double earthRadius = 6378.137;

    /**
     * The latitude of the location (-90 to +90 degrees). 
     */
    public double latitude;

    /**
     * The longitude of the location (-180 to +180 degrees).
     */
    public double longitude;

    /**
     * Constructs a new GeoLocation object with the specified latitude and longitude.
     *
     * @param latitude the latitude coordinate of the location
     * @param longitude the longitude coordinate of the location
     */
    public GeoLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Calculates and returns the distance between this position
     * and another position using the Haversine formula (in meters).
     *
     * @param other the other position
     */
    public double calculateDistance(GeoLocation other) {
        
        double deltaLatitude = (other.latitude * Math.PI / 180) - (this.latitude * Math.PI / 180);
        double deltaLongitude = (other.longitude * Math.PI / 180) - (this.longitude * Math.PI / 180);
        
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2) + Math.cos(this.latitude * Math.PI / 180)
                * Math.cos(other.latitude * Math.PI / 180) 
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = earthRadius * c;
        return d * 1000;
    }

    /**
     * Calculates and returns the angle between this position and the other position.
     *
     * @param other the other GeoLocation
     */
    public double angle(GeoLocation other) {

        double deltaLongitude = (other.longitude - longitude);

        double y = Math.sin(deltaLongitude) * Math.cos(other.latitude);
        double x = Math.cos(latitude) * Math.sin(other.latitude)
                - Math.sin(latitude) * Math.cos(other.latitude) * Math.cos(deltaLongitude);

        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        brng = (brng + 360) % 360;
        //brng = 359 - brng; count degrees counter-clockwise - remove to make clockwise // TODO: Check it

        return brng;
    }

    /**
     * Calculates and returns the next position based on the specified distance and angle.
     *
     * @param distance the distance from this position to the next position 
     * @param angle    the position is calculated in this direction based on the distance
     */
    public GeoLocation nextLocation(double distance, double angle) {
        double r = earthRadius * 1000;
        double lat2 = Math.asin(Math.sin(Math.toRadians(latitude)) * Math.cos(distance / r)
                + Math.cos(Math.toRadians(latitude)) * Math.sin(distance / r) * Math.cos(Math.toRadians(angle)));
        double lon2 = Math.toRadians(longitude) + Math.atan2(
                Math.sin(Math.toRadians(angle)) * Math.sin(distance / r) * Math.cos(Math.toRadians(latitude)),
                Math.cos(distance / r) - Math.sin(Math.toRadians(latitude)) * Math.sin(lat2));
        lat2 = Math.toDegrees(lat2);
        lon2 = Math.toDegrees(lon2);
        return new GeoLocation(lat2, lon2);
    }
    
    /**
     * Returns a string representation of this physical position.
     */
    @Override
    public String toString() {
        return "GeoLocation [latitude=" + latitude + ", longitude=" + longitude + "]";
    }
}