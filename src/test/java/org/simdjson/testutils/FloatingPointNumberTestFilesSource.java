package org.simdjson.testutils;

import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides files with floating point number test cases.
 * <p>
 * The default location of the files is the directory /testdata within the project directory.
 * It can be customized using the system property 'org.simdjson.testdata.dir'.
 * <p>
 * The files are expected to be formatted as described at:
 * <a href="https://github.com/nigeltao/parse-number-fxx-test-data">https://github.com/nigeltao/parse-number-fxx-test-data</a>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(FloatingPointNumberTestFilesProvider.class)
public @interface FloatingPointNumberTestFilesSource {

}
