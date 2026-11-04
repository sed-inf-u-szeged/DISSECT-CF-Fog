package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts SO-SMART trip events into blockchain transactions.
 */
public class TripEventTransactionMapper {

    private final Map<String, Transaction> transactionsByEventId =
            new HashMap<>();

    /**
     * Converts an event into a transaction.
     * Repeated conversion of the same event ID returns
     * the previously created transaction.
     *
     * @param event the trip event to convert
     * @return the corresponding blockchain transaction
     */
    public Transaction toTransaction(TripEvent event) {
        Objects.requireNonNull(event, "Trip event is required.");

        Transaction existing = transactionsByEventId.get(event.getId());

        if (existing != null) {
            return existing;
        }

        String data = serializeEvent(event);
        long size = data.getBytes(StandardCharsets.UTF_8).length;

        Transaction transaction = new Transaction(data, size);

        transactionsByEventId.put(event.getId(), transaction);

        return transaction;
    }

    /**
     * Serializes an event into a structured transaction payload.
     *
     * @param event the event to serialize
     * @return transaction payload
     */
    private String serializeEvent(TripEvent event) {
        StringBuilder data = new StringBuilder();

        data.append("schema=SO_SMART_V1\n");
        data.append("eventId=").append(event.getId()).append('\n');
        data.append("tripId=").append(event.getTripId()).append('\n');
        data.append("type=").append(event.getType()).append('\n');
        data.append("timestampMs=").append(event.getTimestampMs())
                .append('\n');

        TripSegment segment = event.getSegment();

        if (segment != null) {
            data.append("segmentIndex=")
                    .append(event.getSegmentIndex()).append('\n');

            data.append("operator=")
                    .append(segment.getOperator().name()).append('\n');

            data.append("distanceKm=")
                    .append(segment.getDistanceKm()).append('\n');

            data.append("durationMs=")
                    .append(segment.getDurationMs()).append('\n');

            data.append("costHuf=")
                    .append(segment.getCost()).append('\n');
        }

        return data.toString();
    }

    /**
     * Returns the number of unique events converted.
     *
     * @return number of generated transactions
     */
    public int getTransactionCount() {
        return transactionsByEventId.size();
    }
}
