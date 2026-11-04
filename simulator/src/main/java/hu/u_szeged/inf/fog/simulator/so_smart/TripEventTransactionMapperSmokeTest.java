package hu.u_szeged.inf.fog.simulator.so_smart;

import hu.u_szeged.inf.fog.simulator.distributed_ledger.Transaction;

import java.nio.charset.StandardCharsets;

/**
 * Smoke test for converting SO-SMART events into transactions.
 */
public class TripEventTransactionMapperSmokeTest {

    public static void main(String[] args) {
        TripEventTransactionMapper mapper =
                new TripEventTransactionMapper();

        int initialTransactionCount = Transaction.transactions.size();

        TripEvent started = new TripEvent(
                TripEventType.TRIP_STARTED,
                "Trip-1",
                100L,
                -1,
                null
        );

        TripSegment segment = new TripSegment(
                Operator.BIKE_SHARING,
                2.0,
                800L
        );

        TripEvent completedSegment = new TripEvent(
                TripEventType.SEGMENT_COMPLETED,
                "Trip-1",
                900L,
                0,
                segment
        );

        Transaction startTx = mapper.toTransaction(started);
        Transaction segmentTx = mapper.toTransaction(completedSegment);

        // Verify that each event produces a valid transaction.
        if (!startTx.getData().contains("type=TRIP_STARTED")) {
            throw new AssertionError("Trip start event was not serialized.");
        }

        if (!segmentTx.getData().contains("type=SEGMENT_COMPLETED")
                || !segmentTx.getData().contains("operator=BIKE_SHARING")
                || !segmentTx.getData().contains("costHuf=100.0")) {
            throw new AssertionError(
                    "Completed segment data was not serialized correctly.");
        }

        // Verify transaction size calculation.
        long expectedSize = segmentTx.getData()
                .getBytes(StandardCharsets.UTF_8).length;

        if (segmentTx.getSize() != expectedSize) {
            throw new AssertionError("Incorrect transaction size.");
        }

        // Verify that an event is not converted twice.
        if (mapper.toTransaction(completedSegment) != segmentTx) {
            throw new AssertionError(
                    "Duplicate transaction created for the same event.");
        }

        if (mapper.getTransactionCount() != 2
                || Transaction.transactions.size()
                != initialTransactionCount + 2) {
            throw new AssertionError("Incorrect transaction count.");
        }

        System.out.println("=== SO-SMART TRANSACTION MAPPING ===");
        System.out.println("Generated transactions: "
                + mapper.getTransactionCount());

        System.out.println("Start transaction ID: "
                + startTx.getId());

        System.out.println("Segment transaction ID: "
                + segmentTx.getId());

        System.out.println("Segment transaction data:");
        System.out.println(segmentTx.getData());

        System.out.println("PASS | Trip event transaction mapping successful");
    }
}
