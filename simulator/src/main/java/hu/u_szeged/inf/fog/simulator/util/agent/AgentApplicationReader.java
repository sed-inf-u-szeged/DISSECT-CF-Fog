package hu.u_szeged.inf.fog.simulator.util.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import java.io.File;
import java.io.IOException;

public class AgentApplicationReader {

    public static int appCount = 0;

    public static AgentApplication readAgentApplications(String filepath) {
        
        AgentApplication agentApplication = null;
        
        ObjectMapper objectMapper = new ObjectMapper();
        appCount++;

        try {
            agentApplication = objectMapper.readValue(new File(filepath), AgentApplication.class);
            agentApplication.reName();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return agentApplication;
    }
}
