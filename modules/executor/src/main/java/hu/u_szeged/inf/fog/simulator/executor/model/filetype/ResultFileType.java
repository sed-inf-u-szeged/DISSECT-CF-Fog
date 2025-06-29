package hu.u_szeged.inf.fog.simulator.executor.model.filetype;

public enum ResultFileType implements FileType {
    TIMELINE;

    @Override
    public String typeExtension() {
        return ".html";
    }

    @Override
    public String contentType() {
        return "text/html";
    }
}
