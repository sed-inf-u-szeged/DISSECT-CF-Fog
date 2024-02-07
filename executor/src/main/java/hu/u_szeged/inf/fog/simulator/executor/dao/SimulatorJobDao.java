package hu.u_szeged.inf.fog.simulator.executor.dao;

import hu.u_szeged.inf.fog.simulator.executor.dao.model.SimulatorJobDataObject;
import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJob;
import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJobStatus;
import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ResultFileType;
import hu.u_szeged.inf.fog.simulator.result.SimulatorJobResult;
import java.io.File;
import java.util.HashMap;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimulatorJobDao {
    private static final Sort JOB_SORTING = Sort.by(Sort.Order.asc("createdDate")); // TODO: check the ordering!
    private static final String JOB_STATUS_FIELD_NAME = "simulatorJobStatus";
    private static final String ID_FIELD_NAME = "_id";

    @NonNull
    private final MongoTemplate mongoTemplate;
    @NonNull
    private final SimulatorJobRetrieverGridFsDao retrieverGridFsDao;
    @NonNull
    private final SimulatorJobSaverGridFsDao saverGridFsDao;

    public SimulatorJob retrieveNextAvailableJob() {
        var submittedCriteria = Criteria.where(JOB_STATUS_FIELD_NAME).is(SimulatorJobStatus.SUBMITTED);
        var jobCriteria = new Criteria().orOperator(submittedCriteria); 

        var query = Query.query(jobCriteria).with(JOB_SORTING);
        var updateStatus = Update.update(JOB_STATUS_FIELD_NAME, SimulatorJobStatus.PROCESSING);
        var simulatorJobDo = mongoTemplate.findAndModify(query, updateStatus, SimulatorJobDataObject.class);

        return convertJobDataObjectToDomain(simulatorJobDo);
    }

    public void saveSimulatorJobResult(@NonNull String id, @NonNull HashMap<ResultFileType, File> resultFiles,
            @NonNull SimulatorJobResult simulatorJobResult) {
        var savedDbFiles = saverGridFsDao.saveFiles(resultFiles);
        var query = Query.query(Criteria.where(ID_FIELD_NAME).is(id));
        var update = Update.update("results", savedDbFiles).set("simulatorJobResult", simulatorJobResult)
                .set(JOB_STATUS_FIELD_NAME, SimulatorJobStatus.PROCESSED);

        mongoTemplate.updateFirst(query, update, SimulatorJobDataObject.class);
    }

    // TODO: refactor this method!
    public void saveSimulatorJobError(@NonNull String id, long numberOfCalculation) {
        var query = Query.query(Criteria.where(ID_FIELD_NAME).is(id));
        var update = Update.update(JOB_STATUS_FIELD_NAME, SimulatorJobStatus.FAILED);

        mongoTemplate.updateFirst(query, update, SimulatorJobDataObject.class);
    }

    private SimulatorJob convertJobDataObjectToDomain(SimulatorJobDataObject simulatorJobDo) {
        return Optional.ofNullable(simulatorJobDo)
                .map(job -> SimulatorJob.builder().id(job.getId()).user(job.getUser())
                        .simulatorJobStatus(job.getSimulatorJobStatus())
                        .configFiles(retrieverGridFsDao.retrieveFiles(job.getConfigFiles())).build())
                .orElse(null);
    }
}
