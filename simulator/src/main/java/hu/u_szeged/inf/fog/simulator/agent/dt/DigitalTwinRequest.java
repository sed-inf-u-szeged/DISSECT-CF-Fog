package hu.u_szeged.inf.fog.simulator.agent.dt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DigitalTwinRequest {

    public Metadata metadata;
    public List<ResourceNode> resources;
    public Application application;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {

        @JsonProperty("request_id")
        public String requestId;

        @JsonProperty("application_type")
        public String applicationType;

        @JsonProperty("prediction_horizon_min")
        public int predictionHorizonMin;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceNode {

        @JsonProperty("node_id")
        public String nodeId;

        public int cpu;

        @JsonProperty("storage_gb")
        public int storageGb;

        @JsonProperty("memory_mb")
        public int memoryMb;

        public String location;
        public String provider;

        @JsonProperty("node_type")
        public String nodeType;

        @JsonProperty("avg_latency_ms")
        public int avgLatencyMs;

        @JsonProperty("avg_bandwidth_mbps")
        public int avgBandwidthMbps;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Application {

        @JsonProperty("application_id")
        public String applicationId;

        public List<Component> components;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Component {

        @JsonProperty("component_id")
        public String componentId;

        @JsonProperty("mapped_node")
        public String mappedNode;

        @JsonProperty("cpu_request")
        public int cpuRequest;

        @JsonProperty("memory_request_mb")
        public int memoryRequestMb;

        public String workload;

        public JsonNode properties;
    }
}
