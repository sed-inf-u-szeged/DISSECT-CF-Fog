package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;

/**
 * Stores a successfully completed Fog handover for map visualization.
 */
public final class HandoverRecord {

    private final String tripId;
    private final String fromFog;
    private final String toFog;
    private final long timestampMs;
    private final double latitude;
    private final double longitude;

    public HandoverRecord(String tripId, String fromFog, String toFog,
                          long timestampMs, GeoLocation position) {
        this.tripId = tripId;
        this.fromFog = fromFog;
        this.toFog = toFog;
        this.timestampMs = timestampMs;
        this.latitude = position.latitude;
        this.longitude = position.longitude;
    }

    public String getTripId() {
        return tripId;
    }

    public String getFromFog() {
        return fromFog;
    }

    public String getToFog() {
        return toFog;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
