package hu.u_szeged.inf.fog.simulator.agent.dt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ParkingDigitalTwinRequest {

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

        @JsonProperty("scaling_cooldown_ms")
        public long scalingCooldownMs;

        @JsonProperty("ble_polling_interval_ms")
        public long blePollingIntervalMs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceNode {

        @JsonProperty("node_id")
        public String nodeId;

        @JsonProperty("cpu_cores")
        public int cpuCores;

        @JsonProperty("storage_gb")
        public int storageGb;

        @JsonProperty("memory_mb")
        public int memoryMb;

        public String location;
        public String provider;

        @JsonProperty("node_type")
        public String nodeType;

        @JsonProperty("min_power_w")
        public double minPowerW;

        @JsonProperty("idle_power_w")
        public double idlePowerW;

        @JsonProperty("max_power_w")
        public double maxPowerW;

        @JsonProperty("network_latency_ms")
        public int networkLatencyMs;

        @JsonProperty("network_bandwidth_bytes_per_ms")
        public long networkBandwidthBytesPerMs;
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

        @JsonProperty("assigned_resource")
        public String assignedResource;

        @JsonProperty("cpu_request_cores")
        public int cpuRequestCores;

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

        @JsonProperty("battery_level")
        public int batteryLevel;

        public String mode;

        @JsonProperty("nbiot_network_latency_ms")
        public int nbiotNetworkLatencyMs;

        @JsonProperty("nbiot_network_bandwidth_bytes_per_ms")
        public long nbiotNetworkBandwidthBytesPerMs;

        @JsonProperty("ble_network_latency_ms")
        public int bleNetworkLatencyMs;

        @JsonProperty("ble_network_bandwidth_bytes_per_ms")
        public long bleNetworkBandwidthBytesPerMs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Operation {

        public String type;

        @JsonProperty("component_id")
        public String componentId;

        @JsonProperty("target_mode")
        public String targetMode;
    }
}