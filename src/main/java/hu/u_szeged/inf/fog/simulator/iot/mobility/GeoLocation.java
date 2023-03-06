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
        double o_longitude = other.longitude;
        double o_latitude = other.latitude;
        double d_lat = o_latitude * Math.PI / 180 - this.latitude * Math.PI / 180;
        double d_long = o_longitude * Math.PI / 180 - this.longitude * Math.PI / 180;
        double a = Math.sin(d_lat/2) * Math.sin(d_lat/2) +
                Math.cos(this.latitude * Math.PI / 180) * Math.cos(o_latitude * Math.PI / 180) *
                Math.sin(d_long/2) * Math.sin(d_long/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = earthRadius * c;
        return d * 1000;
    }

	@Override
	public String toString() {
		return "GeoLocation [latitude=" + latitude + ", longitude=" + longitude + "]";
	}
	
}
