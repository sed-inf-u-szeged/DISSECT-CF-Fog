package hu.u_szeged.inf.fog.simulator.executor.dao;

import static hu.u_szeged.inf.fog.simulator.executor.util.SimulatorJobFileUtil.saveFileIntoLocalFile;

import hu.u_szeged.inf.fog.simulator.executor.model.filetype.ConfigFileType;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


/**
 * A Grid FS Data Access Object for retrieving job related files.
 *
 * @author Balazs Lehoczki
 */
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimulatorJobRetrieverGridFsDao {

    @NonNull
    private final GridFsTemplate gridFsTemplate;
    @NonNull
    private final GridFsOperations gridFsOperations;

    public MultiValueMap<ConfigFileType, File> retrieveFiles(@NonNull Map<String, ObjectId> inputs) {
        final MultiValueMap<ConfigFileType, File> result = new LinkedMultiValueMap<>();

        for (Map.Entry<String, ObjectId> entry : inputs.entrySet()) {
            var type = ConfigFileType.fromValue(entry.getKey());
            var file = retrieveFile(entry.getValue());

            result.add(type, file);
        }

        return result;
    }

    protected File retrieveFile(ObjectId fileId) {
        return Optional.of(Criteria.where("_id").is(fileId)).map(criteria -> new Query(criteria))
                .map(query -> gridFsTemplate.findOne(query)).map(gridFSFile -> gridFsOperations.getResource(gridFSFile))
                .map(gridFsResource -> saveFileIntoLocalFile(gridFsResource, gridFsResource.getFilename()))
                .orElseThrow(() -> new IllegalStateException("Couldn't retrieve file from DB."));
    }
}
