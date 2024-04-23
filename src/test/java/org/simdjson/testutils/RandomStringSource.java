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
@ArgumentsSource(RandomStringProvider.class)
public @interface RandomStringSource {

    int count() default 10;

    int minChars() default 1;

    int maxChars() default 100;
}
