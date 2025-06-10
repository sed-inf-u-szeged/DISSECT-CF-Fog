package hu.u_szeged.inf.fog.simulator.prediction.parser.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation which could be placed on a Field, for handling multiple variations of names for a JSON string field
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FromJsonFieldAliases {

    /**
     * The possible JSON string field names for parsing into the Java field.
     */
    String[] fieldNames();
}
