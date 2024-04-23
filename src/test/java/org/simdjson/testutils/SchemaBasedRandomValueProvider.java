package org.simdjson.testutils;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class SchemaBasedRandomValueProvider implements ArgumentsProvider, AnnotationConsumer<SchemaBasedRandomValueSource> {

    private static final Set<Class<?>> SUPPORTED_PRIMITIVE_TYPES = Set.of(
            Boolean.class,
            boolean.class,
            String.class,
            Character.class,
            char.class,
            Byte.class,
            byte.class,
            Short.class,
            short.class,
            Integer.class,
            int.class,
            Long.class,
            long.class,
            Float.class,
            float.class,
            Double.class,
            double.class
    );
    private static final GeneratedElement NULL_ELEMENT = new GeneratedElement(null, "null");
    private static final int MIN_ARRAY_ELEMENT = 1;
    private static final int MAX_ARRAY_ELEMENT = 50;

    private Class<?>[] schemas;
    private boolean nulls;

    @Override
    public void accept(SchemaBasedRandomValueSource schemaBasedRandomValueSource) {
        schemas = schemaBasedRandomValueSource.schemas();
        nulls = schemaBasedRandomValueSource.nulls();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Arrays.stream(schemas)
                .map(schema -> {
                    GeneratedElement expected = generate(schema, schema);
                    Class<?>[] parameterTypes = context.getRequiredTestMethod().getParameterTypes();
                    Object[] args = new Object[parameterTypes.length];
                    for (int i = 0; i < args.length; i++) {
                        if (parameterTypes[i] == Class.class) {
                            args[i] = Named.named(schema.getName(), schema);
                        } else if (parameterTypes[i] == String.class) {
                            args[i] = expected.string();
                        } else {
                            args[i] = expected.value();
                        }
                    }
                    return () -> args;
                });
    }

    private GeneratedElement generate(Type type, Class<?> c) {
        if (SUPPORTED_PRIMITIVE_TYPES.contains(c)) {
            return generatePrimitive(type);
        } else if (c.isArray()) {
            return generateArray(c);
        } else if (c == List.class) {
            return generateList((ParameterizedType) type);
        } else {
            Constructor<?> constructor = resolveConstructor(c);
            Parameter[] parameters = constructor.getParameters();
            Object[] args = new Object[parameters.length];
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append('{');
            for (int i = 0; i < args.length; i++) {
                Parameter parameter = parameters[i];
                GeneratedElement generatedElement = generate(parameter.getAnnotatedType().getType(), parameter.getType());
                args[i] = generatedElement.value();
                jsonBuilder.append('"');
                jsonBuilder.append(parameters[i].getName());
                jsonBuilder.append("\": ");
                jsonBuilder.append(generatedElement.string());
            }
            jsonBuilder.append('}');
            try {
                Object o = constructor.newInstance(args);
                return new GeneratedElement(o, jsonBuilder.toString());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private GeneratedElement generateArray(Class<?> type) {
        StringBuilder jsonStringBuilder = new StringBuilder();
        Class<?> elementType = extractElementType(type);
        int len = RandomUtils.nextInt(MIN_ARRAY_ELEMENT, MAX_ARRAY_ELEMENT + 1);
        Object array = Array.newInstance(elementType, len);
        jsonStringBuilder.append('[');
        boolean arrayHasNullElement = false;
        for (int i = 0; i < len; i++) {
            boolean nullElement = nulls && ((!arrayHasNullElement && i == len - 1) || RandomUtils.nextBoolean());
            GeneratedElement element;
            if (nullElement) {
                element = NULL_ELEMENT;
            } else if (elementType.isArray()) {
                element = generateArray(elementType);
            } else {
                element = generateArrayElement(elementType);
            }
            Array.set(array, i, element.value());
            jsonStringBuilder.append(element.string());
            arrayHasNullElement |= nullElement;
            if (i != len - 1) {
                jsonStringBuilder.append(',');
            }
        }
        jsonStringBuilder.append(']');
        return new GeneratedElement(array, jsonStringBuilder.toString());
    }

    private GeneratedElement generateList(ParameterizedType type) {
        StringBuilder jsonStringBuilder = new StringBuilder();
        Type elementType = type.getActualTypeArguments()[0];
        int len = RandomUtils.nextInt(MIN_ARRAY_ELEMENT, MAX_ARRAY_ELEMENT + 1);
        List<Object> list = new ArrayList<>();
        jsonStringBuilder.append('[');
        boolean arrayHasNullElement = false;
        for (int i = 0; i < len; i++) {
            boolean nullElement = nulls && ((!arrayHasNullElement && i == len - 1) || RandomUtils.nextBoolean());
            GeneratedElement element;
            if (nullElement) {
                element = NULL_ELEMENT;
            } else if (elementType instanceof ParameterizedType parameterizedType) {
                element = generate(elementType, (Class<?>) parameterizedType.getRawType());
            } else {
                element = generate(elementType, (Class<?>) elementType);
            }
            list.add(element.value());
            jsonStringBuilder.append(element.string());
            arrayHasNullElement |= nullElement;
            if (i != len - 1) {
                jsonStringBuilder.append(',');
            }
        }
        jsonStringBuilder.append(']');
        return new GeneratedElement(list, jsonStringBuilder.toString());
    }

    private static Class<?> extractElementType(Class<?> c) {
        Class<?> elementType = c.componentType();
        if (elementType == null) {
            return c;
        }
        return elementType;
    }

    private GeneratedElement generateArrayElement(Class<?> elementType) {
        if (SUPPORTED_PRIMITIVE_TYPES.contains(elementType)) {
            return generatePrimitive(elementType);
        }
        return generate(elementType, elementType);
    }

    private Constructor<?> resolveConstructor(Class<?> expectedClass) {
        Constructor<?>[] constructors = expectedClass.getDeclaredConstructors();
        if (constructors.length == 1) {
            Constructor<?> constructor = constructors[0];
            constructor.setAccessible(true);
            return constructor;
        }
        throw new IllegalArgumentException("Unsupported class: " + expectedClass + ". It should has only one constructor.");
    }

    private GeneratedElement generatePrimitive(Type elementType) {
        if (elementType == Boolean.class || elementType == boolean.class) {
            boolean element = RandomUtils.nextBoolean();
            return new GeneratedElement(element, Boolean.toString(element));
        }
        if (elementType == String.class) {
            String element = StringTestData.randomString(1, 50);
            return new GeneratedElement(element, "\"" + element + "\"");
        }
        if (elementType == Character.class || elementType == char.class) {
            String element = StringTestData.randomString(1, 1);
            return new GeneratedElement(StringEscapeUtils.unescapeJson(element).charAt(0), "\"" + element + "\"");
        }
        if (elementType == Byte.class || elementType == byte.class) {
            byte element = NumberTestData.randomByte();
            return new GeneratedElement(element, String.valueOf(element));
        }
        if (elementType == Short.class || elementType == short.class) {
            short element = NumberTestData.randomShort();
            return new GeneratedElement(element, String.valueOf(element));
        }
        if (elementType == Integer.class || elementType == int.class) {
            int element = NumberTestData.randomInt();
            return new GeneratedElement(element, String.valueOf(element));
        }
        if (elementType == Long.class || elementType == long.class) {
            long element = NumberTestData.randomLong();
            return new GeneratedElement(element, String.valueOf(element));
        }
        if (elementType == Float.class || elementType == float.class) {
            float element = NumberTestData.randomFloat();
            return new GeneratedElement(element, String.valueOf(element));
        }
        if (elementType == Double.class || elementType == double.class) {
            double element = NumberTestData.randomDouble();
            return new GeneratedElement(element, String.valueOf(element));
        }
        throw new UnsupportedOperationException("Unsupported type: " + elementType + ". The following classes are supported: " + SUPPORTED_PRIMITIVE_TYPES);
    }

    private record GeneratedElement(Object value, String string) {
    }
}
