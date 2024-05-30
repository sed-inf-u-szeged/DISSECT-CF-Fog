package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;

/**
 * The abstract class representing a template for applications related 
 * to the time series analysis. This class provides methods to manage and 
 * open these applications on different operating systems.
 */
public abstract class IApplication {
    
    /**
     * A list of all registered applications used for prediction.
     */
    public static List<IApplication> predictionApplications = new ArrayList<>();
    
    /**
     * Returns the project path associated with this application.
     * Needs to be overridden.
     */
    public abstract String getProjectLocation();
    
    /**
     * Opens the application on a Windows operating system.
     */
    public abstract Process openWindows() throws Exception;
    
    /**
     * Opens the application on a (Debian-based) Linux operating system.
     */
    public abstract Process openLinux() throws Exception;

    /**
     * Opens the application based on the current operating system. 
     * Currently, only Windows and Linux OS are supported by default.
     */
    public void open() {
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                openWindows();
            } else if (SystemUtils.IS_OS_LINUX) {
                openLinux();
            } else {
                throw new Exception();
            }
            PredictionLogger.info("application", 
                   String.format("Opening '%s' application... (%s)", getClass().getSimpleName(), getProjectLocation()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if an application with the specified name is registered.
     *
     * @param applicationName the name of the application to check
     * @return {@code true} if an application with the specified name is registered,
     *         {@code false} otherwise
     */
    public static boolean hasApplication(String applicationName) {
        for (IApplication applicationInterface 
                : IApplication.predictionApplications) {
            if (applicationInterface.getClass().getSimpleName().equals(applicationName)) {
                return true;
            }
        }
        return false;
    }
}