package hu.u_szeged.inf.fog.simulator.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


/**
 * Utility class providing logging functionality for the simulator.
 */
public final class SimLogger {

    private static final Logger logger = Logger.getLogger("DISSECT-CF-Fog-SimLogger");

    private SimLogger() {}

    /**
     * Logs a runtime event message.
     *
     * @param message the message to log
     */
    public static void logRun(String message) { // TODO: or just a string?
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "[RUN] " + message);
        }
    }

    /**
     * Logs a result-related message.
     *
     * @param message the message to log
     */
    public static void logRes(Object message) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, "[RES] " + message);
        }
    }

    /**
     * Logs a critical error message and aborts execution.
     *
     * @param message the error message to log
     */
    public static void logError(String message) {
        logger.log(Level.SEVERE, "[ERR] " + message);
        throw new IllegalStateException(message);
    }
    
    public static void logEmptyLine() {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "");
        }
    }

    /**
     * Configures logging behavior for the simulation.
     *
     * <p>Logging modes:</p>
     * <ul>
     *   <li>{@code 0} – logging disabled</li>
     *   <li>{@code 1} – full logging (runtime and results)</li>
     *   <li>{@code 2} – result-only logging</li>
     * </ul>
     *
     * @param logLevel logging mode selector
     * @param toFile {@code true} to enable file logging
     */
    public static void setLogging(int logLevel, boolean toFile) {
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
            try {
                h.close();
            } catch (Exception ignored) {

            }
        }

        logger.setUseParentHandlers(false);

        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n");

        Level level = mapLevel(logLevel);
        logger.setLevel(level);
        
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(consoleHandler);

        if (toFile) {
            try {
                Path dir = Paths.get(ScenarioBase.RESULT_DIRECTORY);
                Files.createDirectories(dir);
                Handler fileHandler = new FileHandler(dir.resolve("log.txt").toString(), true);
                fileHandler.setLevel(level);
                fileHandler.setFormatter(new SimpleFormatter());
                logger.addHandler(fileHandler);
            } catch (IOException | SecurityException e) {
                logger.log(Level.SEVERE, "Failed to set file logging: {0}", e.toString());
            }
        }
    }

    private static Level mapLevel(int logLevel) {
        switch (logLevel) {
          case 0:  return Level.OFF;
          case 1:  return Level.INFO;
          case 2:  return Level.WARNING;
          default: return Level.INFO;
        }
    }
}