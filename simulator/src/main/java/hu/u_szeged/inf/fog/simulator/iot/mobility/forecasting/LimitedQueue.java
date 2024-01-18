package hu.u_szeged.inf.fog.simulator.iot.mobility.forecasting;

import java.util.LinkedList;

/**
 * This class represents a queue data type with limited size. The new elements
 * are inserted to the head of the queue. If the queue reaches its max capacity,
 * the oldest item is removed.
 * 
 * @param <T> Type of the elements in the list
 */
public class LimitedQueue<T> extends LinkedList<T> {
    private final int maxSize;

    /**
     * Instantiates a limited queue object
     * 
     * @param maxSize The capacity of the queue
     */
    public LimitedQueue(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("Can't create queue with negative capacity");
        }
        this.maxSize = maxSize;
    }

    /**
     * Adds a new element to the queue. If the current size > capacity, then the
     * oldest element is removed from the queue, maintaining its fixed size.
     * 
     * @param k The element to be added
     * @return Always true
     */
    public boolean add(T k) {
        super.addFirst(k);
        if (size() > maxSize) {
            removeLast();
        }
        return true;
    }

    /**
     * Simple to string method that writes the element in order with space between
     * them
     * 
     * @return The string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        forEach(val -> sb.append(val).append(" "));
        return sb.toString();
    }
}