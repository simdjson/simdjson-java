package org.simdjson.testutils;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.util.stream.IntStream;
import java.util.stream.Stream;

class RandomStringProvider implements ArgumentsProvider, AnnotationConsumer<RandomStringSource> {

    private int count;
    private int minChars;
    private int maxChars;

    @Override
    public void accept(RandomStringSource randomStringSource) {
        count = randomStringSource.count();
        if (count <= 0) {
            throw new IllegalArgumentException("count has to be greater than zero");
        }
        minChars = randomStringSource.minChars();
        if (minChars <= 0) {
            throw new IllegalArgumentException("minChars has to be greater than zero");
        }
        maxChars = randomStringSource.maxChars();
        if (maxChars <= 0 || maxChars == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxChars has to be withing the range of [1, Integer.MAX_VALUE - 1]");
        }
        if (maxChars < minChars) {
            throw new IllegalArgumentException("maxChars has to be greater or equal to minChars");
        }
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        Class<?>[] parameterTypes = context.getRequiredTestMethod().getParameterTypes();
        if (parameterTypes.length != 2) {
            throw new IllegalArgumentException("Test method should have two arguments: an input string and an expected value.");
        }
        if (parameterTypes[0] != String.class) {
            throw new IllegalArgumentException("The first argument must be a String.");
        }
        if (parameterTypes[1] != String.class && parameterTypes[1] != Character.class && parameterTypes[1] != char.class) {
            throw new IllegalArgumentException("The second argument must be either a String, Character, or char.");
        }
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    String jsonStr = StringTestData.randomString(minChars, maxChars);
                    if (parameterTypes[1] == String.class) {
                        return Arguments.of(jsonStr, StringEscapeUtils.unescapeJson(jsonStr));
                    }
                    return Arguments.of(jsonStr, StringEscapeUtils.unescapeJson(jsonStr).charAt(0));
                });
    }
}
