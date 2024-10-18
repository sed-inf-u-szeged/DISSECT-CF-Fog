package hu.u_szeged.inf.fog.simulator.util.tosca;

import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class AgentApplicationReader {

    public static AgentApplication readAgentApplications(String filepath) {
        
        AgentApplication agentApplication = null;
        
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            agentApplication = objectMapper.readValue(new File(filepath), AgentApplication.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return agentApplication;
    }
}
