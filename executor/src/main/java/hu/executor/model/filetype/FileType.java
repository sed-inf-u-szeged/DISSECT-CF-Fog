package hu.executor.model.filetype;

public interface FileType {

    String name();

    String typeExtension();

    String contentType();

    default String fileName() {
        return this.name() + this.typeExtension();
    }
}
