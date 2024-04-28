package org.simdjson.testutils;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

class FloatingPointNumberTestFilesProvider implements ArgumentsProvider, AnnotationConsumer<FloatingPointNumberTestFilesSource> {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return listTestFiles()
                .map(FloatingPointNumberTestFile::new)
                .map(Arguments::of);
    }

    @Override
    public void accept(FloatingPointNumberTestFilesSource annotation) {
    }

    private static Stream<File> listTestFiles() {
        String testDataDir = System.getProperty("org.simdjson.testdata.dir", System.getProperty("user.dir") + "/testdata");
        File[] testFiles = Path.of(testDataDir, "parse-number-fxx-test-data", "data").toFile().listFiles();
        if (testFiles == null) {
            return Stream.empty();
        }
        return Stream.of(testFiles)
                .filter(File::isFile);
    }
}
