package hu.u_szeged.inf.fog.simulator.util;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Provides logging functionality for the simulation.
 */
public class SimLogger {
    
    /**
     * Logger instance for logging simulation events.
     */
    public static Logger simLogger = Logger.getLogger("DISSECT-CF-Fog-SimLogger"); 
    
    /**
     * Logs event occurring runtime.
     *
     * @param string the message to log
     */
    public static void logRun(String string) {
        simLogger.info(string);
    }
    
    /**
     * Logs event occurring after (i.e. logging results).
     *
     * @param string he message to log
     */
    public static void logRes(String string) {
        simLogger.warning(string);
    }
    
    /**
     * Logs critical error event and exits the program.
     *
     * @param string he error message to log
     */
    public static void logError(String string) {
        simLogger.severe(string);
        simLogger.severe("ERROR");
        System.exit(0);
    }
    
    /**
     * Set up logging for the simulation.

     * @param logLevel <br/>
     *      &ensp; 0 - logging is turned off (none of the log messages are shown) <br/>
     *      &ensp; 1 - logging is turned on (each log message is printed) <br/>
     *      &ensp; 2 - logging is partially on (only the results are shown) <br/>
     * @param toFile set to true if you would like to store log messages into file as well
     */
    public static void setLogging(int logLevel, boolean toFile) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s %n");

        switch (logLevel) {
          case 0:
              simLogger.setLevel(Level.OFF);
              break;
          case 1:
              simLogger.setLevel(Level.INFO);
              break;
          case 2:
              simLogger.setLevel(Level.WARNING);        
              break;
          default:
              simLogger.setLevel(Level.INFO);
        }
        
        if (toFile) {
            try {
                FileHandler fh = new FileHandler(ScenarioBase.resultDirectory + "/log.txt");
                simLogger.addHandler(fh);
                SimpleFormatter formatter = new SimpleFormatter();  
                fh.setFormatter(formatter);  
            } catch (SecurityException | IOException e) {
                e.printStackTrace();
            }  
        }
    }
}