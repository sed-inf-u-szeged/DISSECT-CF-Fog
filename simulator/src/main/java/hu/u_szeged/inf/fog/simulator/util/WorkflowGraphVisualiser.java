package hu.u_szeged.inf.fog.simulator.util;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

public class WorkflowGraphVisualiser {

    public static void generateDAG(String scriptpath, String resultDirectory, String ioTworkflowFile)
            throws IOException {
        // ProcessBuilder pb = new ProcessBuilder("python3", scriptpath, workflowfile,
        // resultpath);

        if (System.getProperty("os.name").contains("Windows")) {
            ProcessBuilder pb = new ProcessBuilder("python", scriptpath + "DAG.py", ioTworkflowFile, resultDirectory);
            // System.out.println(pb.command());

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        } else {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptpath + "DAG.py", ioTworkflowFile, resultDirectory);

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            pb.start();
        }

    }

}
