package hu.u_szeged.inf.fog.simulator.iot.mobility;

import hu.u_szeged.inf.fog.simulator.iot.Device;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * The class represents a mobility strategy where a device moves
 * between a series of predefined geographical locations (destinations)
 * at a specified speed.
 */
public class NomadicMobilityStrategy extends MobilityStrategy {

    /**
     * A queue of geographical locations that the device will follow.
     */
    public Queue<GeoLocation> destinations = new LinkedList<>();

    /**
     * Constructs the strategy with the specified start position, speed, and destinations.
     *
     * @param startPosition the initial geographical location of the device
     * @param speed         the speed at which the device moves
     * @param destination   a variable number of destination locations
     */
    public NomadicMobilityStrategy(GeoLocation startPosition, double speed, GeoLocation... destination) {
        this(startPosition, speed, new ArrayList<>(Arrays.asList(destination)));
    }

    /**
     * Constructs the strategy with the specified start position, speed, and a list of destinations.
     *
     * @param startPosition the initial geographical location of the device
     * @param speed         the speed at which the device moves
     * @param destination   a list of destination locations
     */
    public NomadicMobilityStrategy(GeoLocation startPosition, double speed, ArrayList<GeoLocation> destination) {
        this.startPosition = startPosition;
        this.currentPosition = new GeoLocation(startPosition.latitude, startPosition.longitude);
        this.destinations.addAll(destination);
        this.speed = speed;
    }

    /**
     * Moves the device based on the nomadic mobility strategy and 
     * returns the final geographical location after moving.
     *
     * @param device the device to move.
     */
    @Override
    public GeoLocation move(Device device) {
        return setPosition(speed * device.freq);
    }

    /**
     * Keeps updating the position of the device based on the travel distance
     * by peeking positions until the device reaches the required distance.
     *
     * @param travelDistance the distance the device has to travel.
     */
    private GeoLocation setPosition(double travelDistance) {
        GeoLocation dest;
        if (!destinations.isEmpty()) {
            dest = destinations.peek();
            double distance = currentPosition.calculateDistance(dest);
            if (distance > travelDistance) {
                double posX = dest.longitude - currentPosition.longitude;
                double posY = dest.latitude - currentPosition.latitude;
                double normPosX = posX / distance;
                double normPosY = posY / distance;
                currentPosition.longitude = (currentPosition.longitude + normPosX * travelDistance);
                currentPosition.latitude = (currentPosition.latitude + normPosY * travelDistance);
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