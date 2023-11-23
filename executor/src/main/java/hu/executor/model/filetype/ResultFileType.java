package hu.executor.model.filetype;

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
