package hu.u_szeged.inf.fog.simulator.common.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Base configuration and shared constants for simulation scenarios.
 */
public class ScenarioBase {

    private ScenarioBase() {}
    
    public static final long DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000L;
    
    public static final long HOUR_IN_MILLISECONDS = 60 * 60 * 1000L;   
    
    public static final long MINUTE_IN_MILLISECONDS = 60 * 1000L;
    
    public static final double TO_KWH = 1000 * 3_600_000D;
    
    public static final long GB_IN_BYTE = 1_073_741_824L;

    public static final long MB_IN_BYTE = 1_048_576L;
    
    public static final String RESULT_DIRECTORY;

    public static final String RESOURCE_PATH =
            new StringBuilder(System.getProperty("user.dir"))
                    .append(File.separator).append("src")
                    .append(File.separator).append("main")
                    .append(File.separator).append("resources")
                    .append(File.separator).append("demo")
                    .append(File.separator)
                    .toString();

    static {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String timestamp = formatter.format(new Date());

        RESULT_DIRECTORY = new StringBuilder(System.getProperty("user.dir"))
                .append(File.separator)
                .append("sim_res")
                .append(File.separator)
                .append(timestamp)
                .toString();

        new File(RESULT_DIRECTORY).mkdirs();
    }
}
