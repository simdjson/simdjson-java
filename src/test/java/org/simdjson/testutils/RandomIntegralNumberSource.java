package org.simdjson.testutils;

import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(RandomIntegralNumberProvider.class)
public @interface RandomIntegralNumberSource {

    Class<?>[] classes();

    /**
     * If set to true generated test arguments will include the min and max values for a given numeric type.
     */
    boolean includeMinMax();
}
