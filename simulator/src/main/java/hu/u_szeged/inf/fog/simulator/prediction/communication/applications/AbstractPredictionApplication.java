package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;

public abstract class AbstractPredictionApplication {
    
    public static List<AbstractPredictionApplication> APPLICATIONS = new ArrayList<>();
    
    public abstract String getProjectLocation();
    
    public abstract Process openWindows() throws Exception;
    
    public abstract Process openLinux() throws Exception;
    
    public abstract Process openMac() throws Exception;

    public void open() {
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                openWindows();
            } else if (SystemUtils.IS_OS_LINUX) {
                openLinux();
            } else if (SystemUtils.IS_OS_MAC) {
                openMac();
            } else {
                throw new Exception();
            }
            PredictionLogger.info("application", 
                   String.format("Opening '%s' application... (%s)", getClass().getSimpleName(), getProjectLocation()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasApplication(String applicationName) {
        for (AbstractPredictionApplication applicationInterface : AbstractPredictionApplication.APPLICATIONS) {
            if (applicationInterface.getClass().getSimpleName().equals(applicationName)) {
                return true;
            }
        }
        return false;
    }
}
