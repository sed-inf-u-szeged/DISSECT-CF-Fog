package hu.u_szeged.inf.fog.simulator.executor.dao.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
/**
 * User data object.
 */

@Data
@Document("users")
public class User {

    @Id
    String email;
    String password;
    String university;
    int simulationsRun;     //Simulations run in the current period
    int maxSimulations;     //Maximum number of simulations in the current period
    long totalRuntime;      //Total runtime in the current period in seconds
    long maxRuntime;        //Maximum runtime in the current period in seconds
    int resetPeriod;        //Reset period in days
    long lastReset;         //Timestamp of the last reset
}
