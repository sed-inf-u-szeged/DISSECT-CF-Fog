package hu.u_szeged.inf.fog.simulator.distributed_ledger.communication;

/**
 * Interface for messages in the distributed ledger system.
 * This interface can be implemented by different message types to ensure
 * a common structure and behavior.
 */
public interface MessageInterface {
    /**
     * Gets the size of the message.
     *
     * @return the size of the message in bytes
     */
    long getSize();

    /**
     * Gets the type of the message.
     *
     * @return the type of the message as a string
     */
    String getType();
}