package org.simdjson.testutils;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junitpioneer.jupiter.cartesian.CartesianParameterArgumentsProvider;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

class CartesianTestCsvArgumentsProvider implements CartesianParameterArgumentsProvider<CartesianTestCsvRow>  {

    @Override
    public Stream<CartesianTestCsvRow> provideArguments(ExtensionContext context, Parameter parameter) {
        CartesianTestCsv source = Objects.requireNonNull(parameter.getAnnotation(CartesianTestCsv.class));
        return Arrays.stream(source.value())
                .map(row -> row.split(","))
                .peek(row -> {
                    for (int i = 0; i < row.length; i++) {
                        row[i] = row[i].trim();
                    }
                })
                .map(CartesianTestCsvRow::new);
    }
}
