package databack.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Denotes that the annotated method, field, class, etc is thread safe and can be accessed/modified/called by multiple
/// threads without external synchronization.
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.SOURCE)
public @interface ThreadSafe {

}
