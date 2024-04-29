package org.simdjson.testutils;

import java.util.Arrays;

public class CartesianTestCsvRow {

    private final String[] cells;

    CartesianTestCsvRow(String[] cells) {
        this.cells = cells;
    }

    public String getValueAsString(int column) {
        return cells[column];
    }

    public double getValueAsDouble(int column) {
        return Double.parseDouble(cells[column]);
    }

    public float getValueAsFloat(int column) {
        return Float.parseFloat(cells[column]);
    }

    public Object getValue(int column, Class<?> expectedTye) {
        if (expectedTye == Float.class || expectedTye == float.class) {
            return getValueAsFloat(column);
        }
        if (expectedTye == Double.class || expectedTye == double.class) {
            return getValueAsDouble(column);
        }
        throw new UnsupportedOperationException("Unsupported type: " + expectedTye.getName());
    }

    @Override
    public String toString() {
        return Arrays.toString(cells);
    }
}
