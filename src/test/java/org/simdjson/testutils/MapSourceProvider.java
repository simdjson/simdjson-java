package org.simdjson.testutils;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.util.Arrays;
import java.util.stream.Stream;

class MapSourceProvider implements ArgumentsProvider, AnnotationConsumer<MapSource> {

    private MapEntry[] entries;

    @Override
    public void accept(MapSource mapSource) {
        entries = mapSource.value();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Arrays.stream(entries)
                .map(entry -> {
                    Object[] key = null;
                    if (entry.stringKey().length != 0) {
                        key = entry.stringKey();
                    } else if (entry.classKey().length != 0) {
                        key = entry.classKey();
                    }
                    if (key == null) {
                        throw new IllegalArgumentException("Missing key.");
                    }
                    if (key.length > 1) {
                        throw new IllegalArgumentException("Expected one key, got " + key.length);
                    }
                    return Arguments.of(key[0], entry.value());
                });
    }
}
