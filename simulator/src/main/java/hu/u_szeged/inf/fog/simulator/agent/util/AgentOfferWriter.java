package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.common.util.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.common.util.SimLogger;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AgentOfferWriter {

    public static class QosPriority {
        public double energy;
        public double bandwidth;
        public double latency;
        public double price;

        public QosPriority(double energy, double bandwidth, double latency, double price) {
            this.energy = energy;
            this.bandwidth = bandwidth;
            this.latency = latency;
            this.price = price;
        }
    }

    public static class JsonOfferData {
    
        public QosPriority qos_priority;
        public List<Double> reliability;
        public List<Double> energy;
        public List<Double> bandwidth;
        public List<Double> latency;
        public List<Double> price;

        public JsonOfferData(QosPriority qosPriority, List<Double> reliability, List<Double> energy, 
                List<Double> bandwidth, List<Double> latency, List<Double> price) {
            this.qos_priority = qosPriority;
            this.reliability = reliability;
            this.energy = energy;
            this.bandwidth = bandwidth;
            this.latency = latency;
            this.price = price;
        }
    }

    public static String writeOffers(JsonOfferData jsonOfferData, String appName) {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(ScenarioBase.RESULT_DIRECTORY + File.separator + appName + "-offers.json");
        try {
            objectMapper.writer(new DefaultPrettyPrinter()).writeValue(file, jsonOfferData);
        } catch (IOException e) {
            SimLogger.logError("Offers cannot be written in file: " + e);
        }
        return file.getAbsolutePath();
    }
}
