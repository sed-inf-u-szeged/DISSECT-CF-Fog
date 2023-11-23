package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Events {
    public static HashMap<String, List<EventHandler>> eventHandlers = new HashMap<>();
    public interface EventHandler {
         void receive(Object o);
    }

    public static void addEventHandler(String event, EventHandler eventHandler) {
        if (!eventHandlers.containsKey(event)) {
            eventHandlers.put(event, new ArrayList<>());
        }
        eventHandlers.get(event).add(eventHandler);
    }

    public static void addEvent(String event, Object o) {
        if (!eventHandlers.containsKey(event)) {
            return;
        }

        for (EventHandler eventHandler: eventHandlers.get(event)) {
            eventHandler.receive(o);
        }
    }
}
