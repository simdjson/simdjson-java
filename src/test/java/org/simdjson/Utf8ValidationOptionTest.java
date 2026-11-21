package org.simdjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.simdjson.OnDemandJsonIterator.IteratorResult;
import org.simdjson.schemas.RecordWithStringArrayField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.simdjson.testutils.TestUtils.toUtf8;

public class Utf8ValidationOptionTest {

    private static final int CAPACITY = 1024;
    private static final int MAX_DEPTH = 64;

    // A string containing 0xFF, which never occurs in UTF-8.
    private static final byte[] INVALID_UTF8 = {'{', '"', 'f', 'i', 'e', 'l', 'd', '"', ':', '[', '"', 'a', (byte) 0xFF, 'b', '"', ']', '}'};

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"field\": [\"abc\", \"\\u0105\\u20AC\", \"\\uD83D\\uDE00\", \"\"]}",
            "{\"field\": [\"\u0105\u0107\u015B\", \"\u03B1\u03B2\u03B3\", \"\uD83D\uDE00 x\"]}",
            "{\"field\": [], \"other\": {\"a\": [1, 2.5, true, null]}}"
    })
    public void validDocumentParsesIdenticallyWithAndWithoutValidation(String jsonStr) {
        // given
        SimdJsonParser validating = new SimdJsonParser(CAPACITY, MAX_DEPTH, true);
        SimdJsonParser trusting = new SimdJsonParser(CAPACITY, MAX_DEPTH, false);
        byte[] json = toUtf8(jsonStr);

        // when
        String validatingDom = render(validating.parse(json, json.length));
        String trustingDom = render(trusting.parse(json, json.length));
        String[] validatingSchema = validating.parse(json, json.length, RecordWithStringArrayField.class).field();
        String[] trustingSchema = trusting.parse(json, json.length, RecordWithStringArrayField.class).field();
        List<String> validatingOnDemand = onDemandStrings(validating, json, json.length);
        List<String> trustingOnDemand = onDemandStrings(trusting, json, json.length);

        // then
        assertThat(trustingDom).isEqualTo(validatingDom);
        assertThat(trustingSchema).containsExactly(validatingSchema);
        assertThat(trustingOnDemand).isEqualTo(validatingOnDemand);
        assertThat(validatingOnDemand).containsExactly(validatingSchema);
    }

    @Test
    public void defaultConstructorsValidate() {
        // given
        SimdJsonParser defaultParser = new SimdJsonParser();
        SimdJsonParser sizedParser = new SimdJsonParser(CAPACITY, MAX_DEPTH);

        // when
        JsonParsingException defaultEx = assertThrows(JsonParsingException.class, () -> defaultParser.parse(INVALID_UTF8, INVALID_UTF8.length));
        JsonParsingException sizedEx = assertThrows(JsonParsingException.class, () -> sizedParser.parse(INVALID_UTF8, INVALID_UTF8.length));

        // then
        assertThat(defaultEx).hasMessage("The input is not valid UTF-8");
        assertThat(sizedEx).hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void invalidUtf8IsRejectedWithValidation() {
        // given
        SimdJsonParser parser = new SimdJsonParser(CAPACITY, MAX_DEPTH, true);

        // when
        JsonParsingException domEx = assertThrows(JsonParsingException.class,
                () -> parser.parse(INVALID_UTF8, INVALID_UTF8.length));
        JsonParsingException schemaEx = assertThrows(JsonParsingException.class,
                () -> parser.parse(INVALID_UTF8, INVALID_UTF8.length, RecordWithStringArrayField.class));
        JsonParsingException onDemandEx = assertThrows(JsonParsingException.class,
                () -> parser.parseOnDemand(INVALID_UTF8, INVALID_UTF8.length));

        // then
        assertThat(domEx).hasMessage("The input is not valid UTF-8");
        assertThat(schemaEx).hasMessage("The input is not valid UTF-8");
        assertThat(onDemandEx).hasMessage("The input is not valid UTF-8");
    }

    @Test
    public void invalidUtf8IsNotRejectedWithoutValidation() {
        // given
        SimdJsonParser parser = new SimdJsonParser(CAPACITY, MAX_DEPTH, false);
        byte[] expected = {'a', (byte) 0xFF, 'b'};

        // when
        JsonValue dom = parser.parse(INVALID_UTF8, INVALID_UTF8.length);
        int string = dom.firstElement(dom.field(dom.index(), toUtf8("field")));
        byte[] domBytes = Arrays.copyOfRange(dom.stringBytes(), dom.byteOffset(string), dom.byteOffset(string) + dom.byteLength(string));
        String[] schema = parser.parse(INVALID_UTF8, INVALID_UTF8.length, RecordWithStringArrayField.class).field();
        OnDemandJsonIterator it = parser.parseOnDemand(INVALID_UTF8, INVALID_UTF8.length);
        byte[] onDemandBuffer = new byte[16 + OnDemandJsonIterator.STRING_BUFFER_PADDING];
        assertThat(it.startIteratingRootObject()).isEqualTo(IteratorResult.NOT_EMPTY);
        it.getFieldName(onDemandBuffer);
        it.moveToFieldValue();
        assertThat(it.startIteratingArray()).isEqualTo(IteratorResult.NOT_EMPTY);
        int onDemandLength = it.getString(onDemandBuffer);

        // then
        assertThat(domBytes).isEqualTo(expected);
        assertThat(schema).hasSize(1);
        assertThat(Arrays.copyOf(onDemandBuffer, onDemandLength)).isEqualTo(expected);
        assertThat(it.nextArrayElement()).isFalse();
        assertThat(it.nextObjectField()).isFalse();
    }

    private static List<String> onDemandStrings(SimdJsonParser parser, byte[] json, int len) {
        List<String> strings = new ArrayList<>();
        byte[] buffer = new byte[CAPACITY + OnDemandJsonIterator.STRING_BUFFER_PADDING];
        OnDemandJsonIterator it = parser.parseOnDemand(json, len);
        if (it.startIteratingRootObject() == IteratorResult.NOT_EMPTY) {
            do {
                int nameLength = it.getFieldName(buffer);
                it.moveToFieldValue();
                boolean field = new String(buffer, 0, nameLength, UTF_8).equals("field");
                if (field && it.startIteratingArray() == IteratorResult.NOT_EMPTY) {
                    do {
                        int length = it.getString(buffer);
                        strings.add(new String(buffer, 0, length, UTF_8));
                    } while (it.nextArrayElement());
                } else if (!field) {
                    it.skipChild();
                }
            } while (it.nextObjectField());
        }
        return strings;
    }

    private static String render(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        render(value, sb);
        return sb.toString();
    }

    private static void render(JsonValue value, StringBuilder sb) {
        if (value.isObject()) {
            sb.append('{');
            Iterator<Map.Entry<String, JsonValue>> it = value.objectIterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonValue> field = it.next();
                sb.append(field.getKey()).append(':');
                render(field.getValue(), sb);
                sb.append(',');
            }
            sb.append('}');
        } else if (value.isArray()) {
            sb.append('[');
            Iterator<JsonValue> it = value.arrayIterator();
            while (it.hasNext()) {
                render(it.next(), sb);
                sb.append(',');
            }
            sb.append(']');
        } else {
            sb.append(value.isString() ? "s:" : "v:").append(value);
        }
    }
}
