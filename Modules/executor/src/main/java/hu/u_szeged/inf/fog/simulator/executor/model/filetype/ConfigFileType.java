package hu.u_szeged.inf.fog.simulator.executor.model.filetype;

public enum ConfigFileType implements FileType {
    INSTANCES_FILE, 
    APPLIANCES_FILE, 
    DEVICES_FILE,
    IAAS_FILE;

    @Override
    public String typeExtension() {
        return ".xml";
    }

    @Override
    public String contentType() {
        return "text/xml";
    }

    public static ConfigFileType fromValue(String type) {
        return type.startsWith("IAAS_FILE") ? IAAS_FILE : ConfigFileType.valueOf(type);
    }
}
