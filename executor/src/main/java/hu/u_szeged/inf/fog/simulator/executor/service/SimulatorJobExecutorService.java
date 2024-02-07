package hu.u_szeged.inf.fog.simulator.executor.service;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.executor.dao.SimulatorJobDao;
import hu.u_szeged.inf.fog.simulator.executor.model.SimulatorJob;
import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ResultFileType;
import hu.u_szeged.inf.fog.simulator.executor.util.SimulatorJobFileUtil;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityEvent;
import hu.u_szeged.inf.fog.simulator.physical.ComputingAppliance;
import hu.u_szeged.inf.fog.simulator.provider.Provider;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;
import hu.u_szeged.inf.fog.simulator.util.TimelineVisualiser;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.ApplianceModel;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.DeviceModel;
import hu.u_szeged.inf.fog.simulator.util.xmlhandler.InstanceModel;

import static hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType.APPLIANCES_FILE;
import static hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType.DEVICES_FILE;
import static hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType.IAAS_FILE;
import static hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType.INSTANCES_FILE;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimulatorJobExecutorService {

    @NonNull
    private final SimulatorJobDao simulatorJobDao;

    @Scheduled(initialDelay = 0, fixedDelayString = "${dissect-cf.jobs.delay_in_milliseconds}")
    public void runSimulationForTheNextAvailableJob() {
        final var startTime = System.nanoTime();
        System.setProperty("org.springframework.boot.logging.java.SimpleFormatter.format","%5$s %n");
        Optional.ofNullable(simulatorJobDao.retrieveNextAvailableJob()).filter(SimulatorJob::isValid).stream()
                .peek(this::simulationHousekeeping)
                .peek(this::loadSimulationData)
                .peek(job -> SimLogger.setLogging(1, true))
                .peek(job -> Timed.simulateUntilLastEvent())
                .peek(job -> this.saveSimulatorResults(job, startTime))
                .findFirst();
    }

    private void simulationHousekeeping(SimulatorJob simulatorjob) {
        // TODO: other static fields?
        Timed.resetTimed();
        Application.allApplications.clear();
        Device.allDevices.clear();
        ComputingAppliance.allComputingAppliances.clear();

        MobilityEvent.changePositionEventCounter = 0;
        MobilityEvent.changeNodeEventCounter = 0;
        MobilityEvent.connectToNodeEventCounter = 0;
        MobilityEvent.disconnectFromNodeEventCounter = 0;

        for (Provider provider : Provider.providers) {
            provider.cost = 0;
        }
    }

    private void loadSimulationData(@NonNull SimulatorJob simulatorJob) {
        var simulatorJobConfigs = simulatorJob.getConfigFiles();
        var iaasLoaders = Optional.ofNullable(simulatorJobConfigs.get(IAAS_FILE)).stream()
                .flatMap(List::stream)
                .map(file -> Map.entry(file.getName().replaceFirst(".xml", ""), file.getAbsolutePath()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        try {
            DeviceModel.loadDeviceXML(simulatorJobConfigs.getFirst(DEVICES_FILE).getPath());
            InstanceModel.loadInstanceXML(simulatorJobConfigs.getFirst(INSTANCES_FILE).getPath());
            ApplianceModel.loadApplianceXML(simulatorJobConfigs.getFirst(APPLIANCES_FILE).getPath(), iaasLoaders);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSimulatorResults(@NonNull SimulatorJob simulatorJob, long startTime) {
        try {
            // TODO: later we should delete the locally stored files which were already uploaded to the database?
            ScenarioBase.calculateIoTCost();
            var result = ScenarioBase.logBatchProcessing(System.nanoTime() - startTime);
            var resultFiles = new HashMap<ResultFileType, File>();
            var timeline = TimelineVisualiser.generateTimeline(SimulatorJobFileUtil.WORKING_DIR_BASE_PATH);
            resultFiles.put(ResultFileType.TIMELINE, timeline);
            simulatorJobDao.saveSimulatorJobResult(simulatorJob.getId(), resultFiles, result);
        } catch (IOException e) {
            simulatorJobDao.saveSimulatorJobError(simulatorJob.getId(), 1);
            throw new IllegalStateException("Couldn't save simulator result onto the filesystem!", e);
        }
    }

}
