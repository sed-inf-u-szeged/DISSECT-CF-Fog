package hu.u_szeged.inf.fog.simulator.prediction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Helper class for logging prediction-related events.
 */
public class PredictionLogger {
    // TODO: replace to the simulator's logger
    static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static void info(String event, String text) {
        String message = String.format("[INFO   ][%s] (%s) %s", DTF.format(LocalDateTime.now()), event, text);
        System.out.println(message);
    }

    public static void warning(String event, String text) {
        String message = String.format("[WARNING][%s] (%s) %s", DTF.format(LocalDateTime.now()), event, text);
        System.out.println(message);
    }

    public static void error(String event, String text) {
        String message = String.format("[ERROR  ][%s] (%s) %s", DTF.format(LocalDateTime.now()), event, text);
        System.out.println(message);
    }
}