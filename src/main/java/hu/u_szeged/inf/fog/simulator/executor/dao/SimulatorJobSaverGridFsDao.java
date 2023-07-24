package hu.u_szeged.inf.fog.simulator.executor.dao;

import hu.u_szeged.inf.fog.simulator.executor.model.filetype.FileType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

/**
 * A Grid FS Data Access Object for saving job related files.
 *
 * @author Balazs Lehoczki
 */
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimulatorJobSaverGridFsDao {

    @NonNull
    private final GridFsTemplate gridFsTemplate;

    public <T extends FileType> Map<String, ObjectId> saveFiles(@NonNull HashMap<T, File> inputs) {
        final Map<String, ObjectId> result = new LinkedHashMap<>();

        for (HashMap.Entry<T, File> entry : inputs.entrySet()) {
            var type = entry.getKey().name();
            var contentType = entry.getKey().contentType();
            result.put(type, saveFile(contentType, entry.getValue()));
        }

        return result;
    }

    protected ObjectId saveFile(String contentType, File file) {
        try (var inputStream = new FileInputStream(file)) {
            return gridFsTemplate.store(inputStream, file.getName(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save file into the database!");
        }
    }
}
