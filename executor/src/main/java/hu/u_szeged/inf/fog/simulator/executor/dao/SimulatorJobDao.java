package hu.u_szeged.inf.fog.simulator.executor.dao;

import hu.u_szeged.inf.fog.simulator.executor.dao.model.SimulatorJobDataObject;
import hu.u_szeged.inf.fog.simulator.executor.dao.model.User;
import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJob;
import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJobStatus;
import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ResultFileType;
import hu.u_szeged.inf.fog.simulator.util.result.SimulatorJobResult;
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

    public void saveSimulatorJobResult(@NonNull SimulatorJob job, @NonNull HashMap<ResultFileType, File> resultFiles,
            @NonNull SimulatorJobResult simulatorJobResult) {
        var savedDbFiles = saverGridFsDao.saveFiles(resultFiles);
        var query = Query.query(Criteria.where(ID_FIELD_NAME).is(job.getId()));
        var update = Update.update("results", savedDbFiles).set("simulatorJobResult", simulatorJobResult)
                .set(JOB_STATUS_FIELD_NAME, SimulatorJobStatus.PROCESSED);

        mongoTemplate.updateFirst(query, update, SimulatorJobDataObject.class);


        var userQuery = Query.query(Criteria.where("email").is(job.getUser()));
        User user = mongoTemplate.findOne(userQuery, User.class);
        if (user == null) {
            return;
        }
        long lastResetTimestamp = user.getLastReset();
        long resetPeriodDays = user.getResetPeriod();

        //if the last reset time was more than resetPeriodDays ago, reset the totalRuntime
        if (System.currentTimeMillis() - lastResetTimestamp > resetPeriodDays * 24 * 60 * 60 * 1000) {
            var resetUpdate = new Update().set("totalRuntime", 0).set("simulationsRun", 0).set("lastReset", System.currentTimeMillis());
            mongoTemplate.updateFirst(userQuery, resetUpdate, User.class);
        }


        // Append to user's simulations run and total runtime
        long additionalRuntime = simulatorJobResult.getRuntime();
        var userUpdate = new Update()
            .inc("simulationsRun", 1)
            .inc("totalRuntime", additionalRuntime);

        mongoTemplate.updateFirst(userQuery, userUpdate, User.class);
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
                        .deviceCode(job.getDeviceCode())
                        .isDeviceCodeCustom(job.getIsDeviceCodeCustom())
                        .applicationCode(job.getApplicationCode())
                        .isApplicationCodeCustom(job.getIsApplicationCodeCustom())
                        .simulatorJobStatus(job.getSimulatorJobStatus())
                        .configFiles(retrieverGridFsDao.retrieveFiles(job.getConfigFiles())).build())
                .orElse(null);
    }
}
