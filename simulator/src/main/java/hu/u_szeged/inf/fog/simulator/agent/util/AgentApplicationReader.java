package hu.u_szeged.inf.fog.simulator.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import java.io.File;
import java.io.IOException;

/**
 * Utility class for reading an {@link AgentApplication} from a JSON file.
 */
public class AgentApplicationReader {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private AgentApplicationReader() {}
    
    /**
     * Reads an {@link AgentApplication} from the given JSON file path.
     *
     * @param filepath path to the JSON application description file
     */
    public static AgentApplication readJson(String filepath) {
        try {
            AgentApplication agentApplication = objectMapper.readValue(new File(filepath), AgentApplication.class);
            agentApplication.reName();
            return agentApplication;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read application description from " + filepath, e);
        }        
    }
}