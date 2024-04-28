package org.simdjson.testutils;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

class RandomIntegralNumberProvider implements ArgumentsProvider, AnnotationConsumer<RandomIntegralNumberSource> {

    private static final int SEQUENCE_SIZE = 10;

    private Class<?>[] classes;
    private boolean includeMinMax;

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Arrays.stream(classes)
                .flatMap(expectedClass -> {
                    List<Object> numbers = generate(expectedClass);

                    if (!numbers.isEmpty()) {
                        return numbers.stream()
                                .map(num -> createArguments(context, expectedClass, String.valueOf(num), num));
                    }

                    Constructor<?> constructor = resolveConstructor(expectedClass);
                    Parameter[] parameters = constructor.getParameters();
                    Parameter parameter = parameters[0];
                    Class<?> parameterType = parameter.getType();
                    numbers = generate(parameterType);

                    if (!numbers.isEmpty()) {
                        return numbers.stream()
                                .map(num -> {
                                    Object expected = createInstance(constructor, num);
                                    String json = "{\"" + parameter.getName() + "\": " + num + "}";
                                    return createArguments(context, expectedClass, json, expected);
                                });
                    }

                    throw new IllegalArgumentException("Unsupported class: " + expectedClass);
                });
    }

    @Override
    public void accept(RandomIntegralNumberSource numbersSource) {
        classes = numbersSource.classes();
        includeMinMax = numbersSource.includeMinMax();
    }

    private Constructor<?> resolveConstructor(Class<?> expectedClass) {
        Constructor<?>[] constructors = expectedClass.getDeclaredConstructors();
        if (constructors.length == 1) {
            Constructor<?> constructor = constructors[0];
            Parameter[] parameters = constructor.getParameters();
            if (parameters.length == 1) {
                return constructor;
            }
        }
        throw new IllegalArgumentException("Unsupported class: " + expectedClass);
    }

    private List<Object> generate(Class<?> expectedClass) {
        if (expectedClass == Byte.class || expectedClass == byte.class) {
            return generateNumbers(NumberTestData::randomByte, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        if (expectedClass == Short.class || expectedClass == short.class) {
            return generateNumbers(NumberTestData::randomShort, Short.MIN_VALUE, Short.MAX_VALUE);
        }
        if (expectedClass == Integer.class || expectedClass == int.class) {
            return generateNumbers(NumberTestData::randomInt, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (expectedClass == Long.class || expectedClass == long.class) {
            return generateNumbers(NumberTestData::randomLong, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        return Collections.emptyList();
    }

    private <T> List<T> generateNumbers(Supplier<T> generator, T min, T max) {
        List<T> numbers = new ArrayList<>();
        if (includeMinMax) {
            numbers.add(min);
            numbers.add(max);
        }
        int randomSequenceLen = SEQUENCE_SIZE - numbers.size();
        for (int i = 0; i < randomSequenceLen; i++) {
            numbers.add(generator.get());
        }
        return numbers;
    }

    private static Object createInstance(Constructor<?> constructor, Object arg) {
        try {
            return constructor.newInstance(arg);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static Arguments createArguments(ExtensionContext context, Class<?> schema, String json, Object expected) {
        Class<?>[] parameterTypes = context.getRequiredTestMethod().getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            if (parameterTypes[i] == Class.class) {
                args[i] = Named.named(schema.getName(), schema);
            } else if (parameterTypes[i] == String.class) {
                args[i] = json;
            } else {
                args[i] = expected;
            }
        }
        return () -> args;
    }
}
