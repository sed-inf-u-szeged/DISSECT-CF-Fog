package hu.u_szeged.inf.fog.simulator.util;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

/**
 * Provides functionality to generate a Directed Acyclic Graph (DAG) visualization of a workflow.
 */
public class WorkflowGraphVisualiser {

    /**
     * Generates a Directed Acyclic Graph (DAG) visualization for a workflow using a Python script.
     * It is only for workflow-based evaluation, and the setting up the right Python environment 
     * is required.
     *
     * @param scriptpath        the path to the directory containing the Python script
     * @param resultDirectory   the directory where the visualization output will be stored
     * @param ioTworkflowFile   the path to the input file containing the IoT workflow description
     */
    public static void generateDag(String scriptpath, String resultDirectory, String ioTworkflowFile) {

        if (System.getProperty("os.name").contains("Windows")) {
            ProcessBuilder pb = new ProcessBuilder("python", scriptpath + "DAG.py", ioTworkflowFile, resultDirectory);
            // System.out.println(pb.command());

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            try {
                pb.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            ProcessBuilder pb = new ProcessBuilder("python3", scriptpath + "DAG.py", ioTworkflowFile, resultDirectory);

            pb.redirectOutput(Redirect.INHERIT);
            pb.redirectError(Redirect.INHERIT);
            try {
                pb.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}