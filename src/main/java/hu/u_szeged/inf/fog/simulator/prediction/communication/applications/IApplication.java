package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class IApplication {
    public static List<IApplication> APPLICATIONS = new ArrayList<>();
    public abstract String getProjectLocation();
    public abstract Process openWindows() throws Exception;
    public abstract Process openLinux() throws Exception;
    public abstract Process openMac() throws Exception;

    public void open() {
        Process process;
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                process = openWindows();
            } else if (SystemUtils.IS_OS_LINUX) {
                process = openLinux();
            } else if (SystemUtils.IS_OS_MAC) {
                process = openMac();
            } else {
                throw new Exception();
            }
            PredictionLogger.info("application", String.format("Opening '%s' application... (%s)", getClass().getSimpleName(), getProjectLocation()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean hasApplication(String applicationName) {
        for (IApplication iApplication: IApplication.APPLICATIONS) {
            if (iApplication.getClass().getSimpleName().equals(applicationName)) {
                return true;
            }
        }
        return false;
    }
}
