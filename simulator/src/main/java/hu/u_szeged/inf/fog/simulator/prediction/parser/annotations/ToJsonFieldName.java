package hu.u_szeged.inf.fog.simulator.prediction.parser.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation which could be placed on a Field, to signal that it will be converted
 * into a different JSON field name on parsing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToJsonFieldName {

    /**
     * The field name which will be used in the JSON string.
     */
    String value();
}
