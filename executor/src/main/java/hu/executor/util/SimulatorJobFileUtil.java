package hu.executor.util;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.InputStreamResource;


@UtilityClass
public class SimulatorJobFileUtil {

    //public static final String WORKING_DIR = "simulator-jobs";
    private static final String SEPARATOR = File.separator;
    public static final String WORKING_DIR_BASE_PATH = ScenarioBase.resultDirectory;

    public static File saveFileIntoLocalFile(InputStreamResource file, String filename) {
        try (var inputStream = file.getInputStream()) {
            var resultFilePath = String.join(SEPARATOR, WORKING_DIR_BASE_PATH, filename);
            var resultFile = new File(resultFilePath);

            resultFile.getParentFile().mkdirs();
            Files.copy(inputStream, resultFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return resultFile;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save file onto the local file system!");
        }
    }

    public static String getPathForFilename(String filename) {
        return String.join(SEPARATOR, WORKING_DIR_BASE_PATH, filename);
    }
}
