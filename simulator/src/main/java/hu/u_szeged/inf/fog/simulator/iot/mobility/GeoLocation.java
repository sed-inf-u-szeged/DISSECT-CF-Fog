package hu.u_szeged.inf.fog.simulator.iot.mobility;

public class GeoLocation {

    static final double earthRadius = 6378.137;

    public double latitude;

    public double longitude;

    public GeoLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double calculateDistance(GeoLocation other) {
        double otherLongitude = other.longitude;
        double otherLatitude = other.latitude;
        double ddLat = otherLatitude * Math.PI / 180 - this.latitude * Math.PI / 180;
        double ddLong = otherLongitude * Math.PI / 180 - this.longitude * Math.PI / 180;
        double a = Math.sin(ddLat / 2) * Math.sin(ddLat / 2) + Math.cos(this.latitude * Math.PI / 180)
                * Math.cos(otherLatitude * Math.PI / 180) * Math.sin(ddLong / 2) * Math.sin(ddLong / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = earthRadius * c;
        return d * 1000;
    }

    @Override
    public String toString() {
        return "GeoLocation [latitude=" + latitude + ", longitude=" + longitude + "]";
    }

    public double angle(GeoLocation other) {

        double ddLon = (other.longitude - longitude);

        double y = Math.sin(ddLon) * Math.cos(other.latitude);
        double x = Math.cos(latitude) * Math.sin(other.latitude)
                - Math.sin(latitude) * Math.cos(other.latitude) * Math.cos(ddLon);

        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        brng = (brng + 360) % 360;
        brng = 359 - brng; // count degrees counter-clockwise - remove to make clockwise // TODO: Check it

        return brng;
    }

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
}
