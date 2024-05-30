package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import java.io.File;

/**
 * The class provides implementations for starting the Python-based prediction application.
 */
public class PredictorApplication extends IApplication {
    
    public PredictorApplication() {
        IApplication.predictionApplications.add(this);
    }

    @Override
    public String getProjectLocation() {
        String parentPath = new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath();

        String str = new StringBuilder(parentPath).append(File.separator)
                .append("predictor-ui").append(File.separator).append("scripts").toString();

        return str;
    }

    @Override
    public Process openWindows() throws Exception {
        String command =  String.format(String.format(
               "C:/Windows/System32/cmd.exe /c cd %s & start python.exe main.py", getProjectLocation()));
       
        System.out.println("Parancs: " + command);
            
        return Runtime.getRuntime().exec(command);
    }

    @Override
    public Process openLinux() throws Exception {
        return Runtime.getRuntime().exec(String.format("gnome-terminal -- python3.10 %smain.py", getProjectLocation()));
    }
}