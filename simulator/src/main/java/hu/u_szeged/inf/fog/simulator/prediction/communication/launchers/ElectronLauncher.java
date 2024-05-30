package hu.u_szeged.inf.fog.simulator.prediction.communication.launchers;

import java.io.File;

/**
 * The class provides implementations for starting the Electron-based prediction application.
 */
public class ElectronLauncher extends Launcher {
      
    public ElectronLauncher() {
        Launcher.predictionApplications.add(this);
    }

    @Override
    public String getProjectLocation() {
        String parentPath = new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath();

        String str = new StringBuilder(parentPath).append(File.separator)
                .append("predictor-ui").append(File.separator).append("ui").toString();

        return str;
    }

    @Override
    public Process openWindows() throws Exception {
        String startCommand = (!buildExists()) ? "build_and_run" : "run";
        return Runtime.getRuntime().exec(
                String.format("C:/Windows/System32/cmd.exe /c cd %s & start npm.cmd run electron:%s", 
                        getProjectLocation(), startCommand));
    }

    @Override
    public Process openLinux() throws Exception {
        String startCommand = (!buildExists()) ? "build_and_run" : "run";
        return Runtime.getRuntime().exec(
                String.format("gnome-terminal --working-directory=%s -- npm run electron:%s", 
                        getProjectLocation(), startCommand));
    }

    private boolean buildExists() {
        return new File(new StringBuilder(
                getProjectLocation()).append("dist").append(File.separator).append("dissect-cf-predictor-ui").toString()
        ).exists();
    }
}