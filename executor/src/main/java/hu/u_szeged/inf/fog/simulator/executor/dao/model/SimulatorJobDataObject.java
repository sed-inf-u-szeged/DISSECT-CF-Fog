package hu.u_szeged.inf.fog.simulator.executor.dao.model;

import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJobStatus;
import hu.u_szeged.inf.fog.simulator.util.result.SimulatorJobResult;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
/**
 * Data object (DO) for the simulator-jobs collection, which is storing the
 * dissect-cf jobs.
 *
 * @author Balazs Lehoczki
 */

@Data
@Builder
@Document("simulator_jobs")
public class SimulatorJobDataObject {

    @Id
    private String id;
    private String user;
    private SimulatorJobStatus simulatorJobStatus;
    private Map<String, ObjectId> configFiles;
    private Map<String, ObjectId> resultFiles;
    private SimulatorJobResult simulatorJobResult;
    private String deviceCode;
    private String isDeviceCodeCustom;
    private String applicationCode;
    private String isApplicationCodeCustom;
    private Long runTime;

    @CreatedDate
    private LocalDateTime createdDate;
    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}
