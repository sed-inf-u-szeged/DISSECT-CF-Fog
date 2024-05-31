package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import java.io.File;

/**
 * The class provides implementations for starting the Python-based prediction application.
 */
public class PredictorLauncher extends Launcher {
    
    public PredictorLauncher() {
        Launcher.predictionApplications.add(this);
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
       
        System.out.println("Command: " + command);
            
        return Runtime.getRuntime().exec(command);
    }

    @Override
    public Process openLinux() throws Exception {
        String command = String.format("gnome-terminal -- python3.10 %s/main.py", getProjectLocation());

        System.out.println("Command: " + command);
        
        return Runtime.getRuntime().exec(command);
    }
}