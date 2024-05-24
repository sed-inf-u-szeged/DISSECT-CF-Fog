package hu.u_szeged.inf.fog.simulator.prediction.communication.applications;

import java.io.File;

public class PredictorApplication extends AbstractPredictionApplication {
    public PredictorApplication() {
        AbstractPredictionApplication.APPLICATIONS.add(this);
    }

    @Override
    public String getProjectLocation() {
        return new StringBuilder(System.getProperty("user.dir"))
                .append(File.separator).append("src").append(File.separator).append("main").append(File.separator)
                .append("resources").append(File.separator).append("prediction")
                .append(File.separator).append("scripts")
                .append(File.separator).toString();
    }

    @Override
    public Process openWindows() throws Exception {
        return Runtime.getRuntime().exec(
                String.format("C:/Windows/System32/cmd.exe /c cd %s & start python.exe main.py", getProjectLocation()));
    }

    @Override
    public Process openLinux() throws Exception {
        return Runtime.getRuntime().exec(String.format("gnome-terminal -- python3.10 %smain.py", getProjectLocation()));
       
        /*
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c","python3.11" + " " + getProjectLocation() + "main.py");
        System.out.println(pb.command());

        pb.redirectOutput(Redirect.INHERIT);
        pb.redirectError(Redirect.INHERIT);
        return pb.start();
        */
    }

    @Override
    public Process openMac() throws Exception {
        return null;
    }
}
