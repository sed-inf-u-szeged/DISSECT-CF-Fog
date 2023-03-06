package hu.u_szeged.inf.fog.simulator.iot.mobility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class NomadicMobilityStrategy extends MobilityStrategy{

	public Queue<GeoLocation> destinations = new LinkedList<>();
	
	public double speed;

    public NomadicMobilityStrategy(GeoLocation startPosition, double speed, GeoLocation ... destination) {
    	this(startPosition, speed, new ArrayList<>(Arrays.asList(destination)));
    }
    
    public NomadicMobilityStrategy(GeoLocation startPosition, double speed, ArrayList<GeoLocation> destination) {
    	this.startPosition = startPosition;
        this.currentPosition = new GeoLocation(startPosition.latitude, startPosition.longitude);
        this.destinations.addAll(destination);
        this.speed = speed;
    }

    @Override
    public GeoLocation move(long freq) {
        return setPosition(speed *freq);
    }

    private GeoLocation setPosition(double travelDistance) {
        GeoLocation dest;
        if(!destinations.isEmpty()) {
            dest = destinations.peek();
                double distance = currentPosition.calculateDistance(dest);
                if (distance > travelDistance) {
                    double posX = dest.longitude - currentPosition.longitude;
                    double posY = dest.latitude - currentPosition.latitude;
                    double norm_posX = posX / distance;
                    double norm_posY = posY / distance;
                    currentPosition.longitude = (currentPosition.longitude + norm_posX * travelDistance);
                    currentPosition.latitude = (currentPosition.latitude + norm_posY * travelDistance);
                    return currentPosition;
                } else {
                    double remained = travelDistance - distance;
                    currentPosition = destinations.poll();
                    return setPosition(remained);
                }
        }
        return currentPosition;
    }
}
