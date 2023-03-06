package hu.u_szeged.inf.fog.simulator.iot.mobility;

public abstract class MobilityStrategy {
	
	public GeoLocation startPosition;

	public GeoLocation currentPosition;
	
	public abstract GeoLocation move(long freq);
}
