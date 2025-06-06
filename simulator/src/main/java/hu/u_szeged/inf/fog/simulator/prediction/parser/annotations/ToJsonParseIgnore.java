package hu.u_szeged.inf.fog.simulator.prediction.parser.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation which could be placed on a Field, to ignore the Field while parsing to JSON.
 * As a result the field will not show up in the converted JSON object.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToJsonParseIgnore {
}
