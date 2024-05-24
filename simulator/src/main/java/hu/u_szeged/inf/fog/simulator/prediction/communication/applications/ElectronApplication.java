package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import java.io.File;

public class ElectronApplication extends AbstractPredictionApplication {
    private boolean development;
    
    public ElectronApplication() {
        AbstractPredictionApplication.APPLICATIONS.add(this);
        this.development = true;
    }

    public ElectronApplication(boolean development) {
        AbstractPredictionApplication.APPLICATIONS.add(this);
        this.development = development;
    }

    @Override
    public String getProjectLocation() {
        return new StringBuilder(System.getProperty("user.dir"))
                .append(File.separator).append("src").append(File.separator).append("main").append(File.separator)
                .append("resources").append(File.separator).append("prediction").append(File.separator).append("ui")
                .append(File.separator).toString();
    }

    @Override
    public Process openWindows() throws Exception {
        String startCommand = (development || !buildExists()) ? "build_and_run" : "run";
        return Runtime.getRuntime().exec(
                String.format("C:/Windows/System32/cmd.exe /c cd %s & start npm.cmd run electron:%s", 
                        getProjectLocation(), startCommand));
    }

    @Override
    public Process openLinux() throws Exception {
        String startCommand = (development || !buildExists()) ? "build_and_run" : "run";
        return Runtime.getRuntime().exec(
                String.format("gnome-terminal --working-directory=%s -- npm run electron:%s", 
                        getProjectLocation(), startCommand));
    }

    @Override
    public Process openMac() throws Exception {
        return null;
    }

    private boolean buildExists() {
        return new File(new StringBuilder(
                getProjectLocation()).append("dist").append(File.separator).append("dissect-cf-predictor-ui").toString()
        ).exists();
    }
}
