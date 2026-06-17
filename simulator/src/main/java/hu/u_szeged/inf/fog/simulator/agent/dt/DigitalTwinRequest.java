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
    public List<Operation> operations;

    @JsonProperty("scenario_id")
    public String scenarioId;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {

        @JsonProperty("request_id")
        public String requestId;

        @JsonProperty("application_type")
        public String applicationType;

        @JsonProperty("prediction_horizon_min")
        public int predictionHorizonMin;

        @JsonProperty("sound_level_threshold")
        public int soundLevelThreshold;

        @JsonProperty("cpu_temperature_threshold")
        public double cpuTemperatureThreshold;

        @JsonProperty("min_cpu_temperature")
        public double minCpuTemperature;

        @JsonProperty("max_cpu_temperature")
        public double maxCpuTemperature;

        @JsonProperty("min_container_count")
        public int minContainerCount;

        @JsonProperty("cpu_load_scale_up")
        public double cpuLoadScaleUp;

        @JsonProperty("cpu_load_scale_down")
        public double cpuLoadScaleDown;

        @JsonProperty("scaling_cooldown_ms")
        public long scalingCooldown;
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

        @JsonProperty("min_power")
        public double minPower;

        @JsonProperty("idle_power")
        public double idlePower;

        @JsonProperty("max_power")
        public double maxPower;

        @JsonProperty("latency_ms")
        public int latencyMs;

        @JsonProperty("bandwidth_mbps")
        public int bandwidthMbps;
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

        public ComponentProperties properties;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentProperties {

        @JsonProperty("component_type")
        public String componentType;

        @JsonProperty("image_size_bytes")
        public long imageSizeBytes;

        public Boolean classifier;

        @JsonProperty("queue_length")
        public int queueLength;

        @JsonProperty("cpu_temperature")
        public double cpuTemperature;

        public Boolean inside;

        public Boolean sun;

        //public Integer instances;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Operation {

        public String type;

        @JsonProperty("component_id")
        public String componentId;

        @JsonProperty("target_node")
        public String targetNode;

        public Boolean classifier;
    }
}
