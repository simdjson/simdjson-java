package org.simdjson.testutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

public class FloatingPointNumberTestFile {

    private final File file;

    FloatingPointNumberTestFile(File file) {
        this.file = file;
    }

    public FloatingPointNumberTestCasesIterator iterator() throws IOException {
        return new FloatingPointNumberTestCasesIterator(file);
    }

    @Override
    public String toString() {
        return file.toString();
    }

    public record FloatingPointNumberTestCase(int line, String input, float expectedFloat, double expectedDouble) {

    }

    public static class FloatingPointNumberTestCasesIterator implements Iterator<FloatingPointNumberTestCase>, AutoCloseable {

        private final BufferedReader br;

        private int nextLineNo = 0;
        private String nextLine;

        private FloatingPointNumberTestCasesIterator(File file) throws IOException {
            br = new BufferedReader(new FileReader(file));
            moveToNextLine();
        }

        @Override
        public boolean hasNext() {
            return nextLine != null;
        }

        @Override
        public FloatingPointNumberTestCase next() {
            String[] cells = nextLine.split(" ");
            float expectedFloat = Float.intBitsToFloat(Integer.decode("0x" + cells[1]));
            double expectedDouble = Double.longBitsToDouble(Long.decode("0x" + cells[2]));
            String input = readInputNumber(cells[3]);
            try {
                moveToNextLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new FloatingPointNumberTestCase(nextLineNo, input, expectedFloat, expectedDouble);
        }

        @Override
        public void close() throws IOException {
            br.close();
        }

        private void moveToNextLine() throws IOException {
            nextLine = br.readLine();
            nextLineNo++;
        }

        private static String readInputNumber(String input) {
            boolean isDouble = input.indexOf('e') >= 0 || input.indexOf('E') >= 0 || input.indexOf('.') >= 0;
            if (isDouble) {
                if (input.startsWith(".")) {
                    input = "0" + input;
                }
                return input.replaceFirst("\\.[eE]", ".0e");
            }
            return input + ".0";
        }
    }
}
