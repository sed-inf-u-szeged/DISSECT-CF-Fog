package hu.u_szeged.inf.fog.simulator.common.util;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class RepoFileManager {

    public static void mergeFiles(Repository repo, StorageObject newFile, String name){
        StorageObject originalFile = repo.lookup(name);
        if(originalFile != null){
            repo.deregisterObject(name);
        }

        repo.deregisterObject(newFile);
        StorageObject fileToSave = new StorageObject(name,
                originalFile != null ? originalFile.size + newFile.size : newFile.size, false);
        repo.registerObject(fileToSave);
    }
}
