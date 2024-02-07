package hu.u_szeged.inf.fog.simulator.executor.model;

import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType;
import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ResultFileType;
import hu.u_szeged.inf.fog.simulator.result.SimulatorJobResult;
import java.io.File;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.util.MultiValueMap;

@Value
@Builder
public class SimulatorJob {

    @Id
    private String id;
    private String user;
    private SimulatorJobStatus simulatorJobStatus;
    private MultiValueMap<ConfigFileType, File> configFiles;
    private MultiValueMap<ResultFileType, File> resultFiles;
    private SimulatorJobResult simulatorJobResult;

    public boolean isValid() {
        return Stream.of(ConfigFileType.values()).map(configFiles::containsKey).allMatch(Boolean::booleanValue);
    }
}
