package org.simdjson.synthea;

import org.simdjson.JsonValue;
import org.simdjson.OnDemandJsonIterator;
import org.simdjson.OnDemandJsonIterator.IteratorResult;
import org.simdjson.OnDemandJsonIterator.ValueType;
import org.simdjson.SimdJsonParser;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.simdjson.synthea.SyntheaChecksum.ABSENT;
import static org.simdjson.synthea.SyntheaChecksum.CONDITION;
import static org.simdjson.synthea.SyntheaChecksum.ENCOUNTER;
import static org.simdjson.synthea.SyntheaChecksum.OBSERVATION;
import static org.simdjson.synthea.SyntheaChecksum.OTHER;
import static org.simdjson.synthea.SyntheaChecksum.PATIENT;
import static org.simdjson.synthea.SyntheaChecksum.hashNumber;
import static org.simdjson.synthea.SyntheaChecksum.hashString;

/**
 * The extraction task, implemented once per parsing mode. Every mode must produce the same checksum and
 * counts; {@link SyntheaBundleVerifier} checks this.
 * <p>
 * For every {@code entry[i].resource} that is a JSON object, in document order, the following values are
 * folded into the checksum, always in this order:
 * <pre>
 *   all resources : resourceType, id
 *   Patient       : birthDate, gender
 *   Encounter     : period.start, class.code
 *   Observation   : code.coding[0].code, effectiveDateTime, valueQuantity.value (number), valueQuantity.unit
 *   Condition     : code.coding[0].code, onsetDateTime
 * </pre>
 * and the resource is counted under its type (anything else, including a missing resourceType, counts
 * as "other"). Values are strings unless marked otherwise. A missing value, {@code null}, or a value of
 * another JSON type folds {@link SyntheaChecksum#ABSENT}. If a key occurs more than once in an object the
 * modes may disagree (JSON leaves this undefined); the verifier would report it.
 * <p>
 * Not thread-safe: one instance per thread.
 */
public final class SyntheaExtractor {

    public enum Mode {
        DOM_GET_STRING("domGetString") {
            @Override
            void run(SyntheaExtractor extractor, byte[] buffer, int length) {
                extractor.domGetString(buffer, length);
            }
        },
        SCHEMA_BASED("schemaBased") {
            @Override
            void run(SyntheaExtractor extractor, byte[] buffer, int length) {
                extractor.schemaBased(buffer, length);
            }
        },
        DOM_GET_BYTES("domGetBytes") {
            @Override
            void run(SyntheaExtractor extractor, byte[] buffer, int length) {
                extractor.domGetBytes(buffer, length);
            }
        },
        DOM_INDEX("domIndex") {
            @Override
            void run(SyntheaExtractor extractor, byte[] buffer, int length) {
                extractor.domIndex(buffer, length);
            }
        },
        ON_DEMAND("onDemand") {
            @Override
            void run(SyntheaExtractor extractor, byte[] buffer, int length) {
                extractor.onDemand(buffer, length);
            }
        };

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        abstract void run(SyntheaExtractor extractor, byte[] buffer, int length);
    }

    /** System property selecting UTF-8 validation for every parser of the benchmark: true (default) or false. */
    public static final String VALIDATE_UTF8_PROPERTY = "synthea.validateUtf8";

    private final SimdJsonParser parser;
    private final SyntheaChecksum sum = new SyntheaChecksum();
    private final int maxDocumentLength;

    /**
     * @param maxDocumentLength the length of the largest document this instance will parse
     * @param validateUtf8      passed to {@link SimdJsonParser#SimdJsonParser(int, int, boolean)}
     */
    public SyntheaExtractor(int maxDocumentLength, boolean validateUtf8) {
        this.maxDocumentLength = maxDocumentLength;
        parser = new SimdJsonParser(maxDocumentLength + SyntheaCorpus.PADDING, 1024, validateUtf8);
    }

    /** Reads {@link #VALIDATE_UTF8_PROPERTY}; anything but "true" or "false" is rejected. */
    public static boolean validateUtf8FromSystemProperties() {
        String value = System.getProperty(VALIDATE_UTF8_PROPERTY, "true");
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException("System property " + VALIDATE_UTF8_PROPERTY
                    + " must be true or false, but is '" + value + "'.");
        };
    }

    public SimdJsonParser parser() {
        return parser;
    }

    public SyntheaChecksum sum() {
        return sum;
    }

    public void run(Mode mode, byte[] buffer, int length) {
        mode.run(this, buffer, length);
    }

    private static int typeOf(String resourceType) {
        if (resourceType == null) {
            return OTHER;
        }
        return switch (resourceType) {
            case "Patient" -> PATIENT;
            case "Encounter" -> ENCOUNTER;
            case "Observation" -> OBSERVATION;
            case "Condition" -> CONDITION;
            default -> OTHER;
        };
    }

    private static long hashNullableString(String value) {
        return value == null ? ABSENT : hashString(value);
    }

    // ---------------------------------------------------------------------------------------------------
    // domGetString: DOM (tape) with JsonValue.get(String) and arrayIterator().
    // ---------------------------------------------------------------------------------------------------

    public void domGetString(byte[] buffer, int length) {
        JsonValue bundle = parser.parse(buffer, length);
        if (!bundle.isObject()) {
            return;
        }
        JsonValue entries = bundle.get("entry");
        if (entries == null || !entries.isArray()) {
            return;
        }
        Iterator<JsonValue> it = entries.arrayIterator();
        while (it.hasNext()) {
            JsonValue entry = it.next();
            if (!entry.isObject()) {
                continue;
            }
            JsonValue resource = entry.get("resource");
            if (resource != null && resource.isObject()) {
                resourceGetString(resource);
            }
        }
    }

    private void resourceGetString(JsonValue resource) {
        String resourceType = stringOrNull(resource.get("resourceType"));
        int type = typeOf(resourceType);
        sum.fold(hashNullableString(resourceType));
        sum.fold(stringHash(resource.get("id")));
        switch (type) {
            case PATIENT -> {
                sum.fold(stringHash(resource.get("birthDate")));
                sum.fold(stringHash(resource.get("gender")));
            }
            case ENCOUNTER -> {
                sum.fold(stringHash(member(resource.get("period"), "start")));
                sum.fold(stringHash(member(resource.get("class"), "code")));
            }
            case OBSERVATION -> {
                sum.fold(stringHash(firstCodingCode(resource.get("code"))));
                sum.fold(stringHash(resource.get("effectiveDateTime")));
                JsonValue quantity = resource.get("valueQuantity");
                sum.fold(numberHash(member(quantity, "value")));
                sum.fold(stringHash(member(quantity, "unit")));
            }
            case CONDITION -> {
                sum.fold(stringHash(firstCodingCode(resource.get("code"))));
                sum.fold(stringHash(resource.get("onsetDateTime")));
            }
            default -> {
            }
        }
        sum.count(type);
    }

    private static JsonValue member(JsonValue object, String name) {
        return object != null && object.isObject() ? object.get(name) : null;
    }

    private static JsonValue firstCodingCode(JsonValue codeableConcept) {
        JsonValue coding = member(codeableConcept, "coding");
        if (coding == null || !coding.isArray()) {
            return null;
        }
        Iterator<JsonValue> it = coding.arrayIterator();
        return it.hasNext() ? member(it.next(), "code") : null;
    }

    private static String stringOrNull(JsonValue value) {
        return value != null && value.isString() ? value.asString() : null;
    }

    private static long stringHash(JsonValue value) {
        return hashNullableString(stringOrNull(value));
    }

    private static long numberHash(JsonValue value) {
        if (value == null) {
            return ABSENT;
        }
        if (value.isLong()) {
            return hashNumber(value.asLong());
        }
        if (value.isDouble()) {
            return hashNumber(value.asDouble());
        }
        return ABSENT;
    }

    // ---------------------------------------------------------------------------------------------------
    // schemaBased: upstream's schema-based parsing into records (SyntheaSchema).
    // ---------------------------------------------------------------------------------------------------

    public void schemaBased(byte[] buffer, int length) {
        SyntheaSchema.Bundle bundle = parser.parse(buffer, length, SyntheaSchema.Bundle.class);
        if (bundle == null || bundle.entry() == null) {
            return;
        }
        for (SyntheaSchema.Entry entry : bundle.entry()) {
            if (entry != null && entry.resource() != null) {
                resourceSchema(entry.resource());
            }
        }
    }

    private void resourceSchema(SyntheaSchema.Resource resource) {
        int type = typeOf(resource.resourceType());
        sum.fold(hashNullableString(resource.resourceType()));
        sum.fold(hashNullableString(resource.id()));
        switch (type) {
            case PATIENT -> {
                sum.fold(hashNullableString(resource.birthDate()));
                sum.fold(hashNullableString(resource.gender()));
            }
            case ENCOUNTER -> {
                SyntheaSchema.Period period = resource.period();
                SyntheaSchema.Coding encounterClass = resource.encounterClass();
                sum.fold(hashNullableString(period == null ? null : period.start()));
                sum.fold(hashNullableString(encounterClass == null ? null : encounterClass.code()));
            }
            case OBSERVATION -> {
                SyntheaSchema.Quantity quantity = resource.valueQuantity();
                sum.fold(hashNullableString(firstCodingCode(resource.code())));
                sum.fold(hashNullableString(resource.effectiveDateTime()));
                sum.fold(quantity == null || quantity.value() == null ? ABSENT : hashNumber(quantity.value()));
                sum.fold(hashNullableString(quantity == null ? null : quantity.unit()));
            }
            case CONDITION -> {
                sum.fold(hashNullableString(firstCodingCode(resource.code())));
                sum.fold(hashNullableString(resource.onsetDateTime()));
            }
            default -> {
            }
        }
        sum.count(type);
    }

    private static String firstCodingCode(SyntheaSchema.CodeableConcept codeableConcept) {
        if (codeableConcept == null) {
            return null;
        }
        List<SyntheaSchema.Coding> coding = codeableConcept.coding();
        if (coding == null || coding.isEmpty() || coding.get(0) == null) {
            return null;
        }
        return coding.get(0).code();
    }

    // ---------------------------------------------------------------------------------------------------
    // Pre-encoded keys and type names shared by domGetBytes, domIndex and onDemand.
    // ---------------------------------------------------------------------------------------------------

    private static final byte[] K_ENTRY = utf8("entry");
    private static final byte[] K_RESOURCE = utf8("resource");
    private static final byte[] K_RESOURCE_TYPE = utf8("resourceType");
    private static final byte[] K_ID = utf8("id");
    private static final byte[] K_BIRTH_DATE = utf8("birthDate");
    private static final byte[] K_GENDER = utf8("gender");
    private static final byte[] K_PERIOD = utf8("period");
    private static final byte[] K_START = utf8("start");
    private static final byte[] K_CLASS = utf8("class");
    private static final byte[] K_CODE = utf8("code");
    private static final byte[] K_CODING = utf8("coding");
    private static final byte[] K_EFFECTIVE_DATE_TIME = utf8("effectiveDateTime");
    private static final byte[] K_VALUE_QUANTITY = utf8("valueQuantity");
    private static final byte[] K_VALUE = utf8("value");
    private static final byte[] K_UNIT = utf8("unit");
    private static final byte[] K_ONSET_DATE_TIME = utf8("onsetDateTime");

    private static final byte[] T_PATIENT = utf8("Patient");
    private static final byte[] T_ENCOUNTER = utf8("Encounter");
    private static final byte[] T_OBSERVATION = utf8("Observation");
    private static final byte[] T_CONDITION = utf8("Condition");

    private static byte[] utf8(String s) {
        return s.getBytes(UTF_8);
    }

    // ---------------------------------------------------------------------------------------------------
    // domGetBytes: as domGetString, but JsonValue.get(byte[]) with pre-encoded keys. Values are still
    // read with asString(), so the difference to domGetString is only the key encoding.
    // ---------------------------------------------------------------------------------------------------

    public void domGetBytes(byte[] buffer, int length) {
        JsonValue bundle = parser.parse(buffer, length);
        if (!bundle.isObject()) {
            return;
        }
        JsonValue entries = bundle.get(K_ENTRY);
        if (entries == null || !entries.isArray()) {
            return;
        }
        Iterator<JsonValue> it = entries.arrayIterator();
        while (it.hasNext()) {
            JsonValue entry = it.next();
            if (!entry.isObject()) {
                continue;
            }
            JsonValue resource = entry.get(K_RESOURCE);
            if (resource != null && resource.isObject()) {
                resourceGetBytes(resource);
            }
        }
    }

    private void resourceGetBytes(JsonValue resource) {
        String resourceType = stringOrNull(resource.get(K_RESOURCE_TYPE));
        int type = typeOf(resourceType);
        sum.fold(hashNullableString(resourceType));
        sum.fold(stringHash(resource.get(K_ID)));
        switch (type) {
            case PATIENT -> {
                sum.fold(stringHash(resource.get(K_BIRTH_DATE)));
                sum.fold(stringHash(resource.get(K_GENDER)));
            }
            case ENCOUNTER -> {
                sum.fold(stringHash(member(resource.get(K_PERIOD), K_START)));
                sum.fold(stringHash(member(resource.get(K_CLASS), K_CODE)));
            }
            case OBSERVATION -> {
                sum.fold(stringHash(firstCodingCodeBytes(resource.get(K_CODE))));
                sum.fold(stringHash(resource.get(K_EFFECTIVE_DATE_TIME)));
                JsonValue quantity = resource.get(K_VALUE_QUANTITY);
                sum.fold(numberHash(member(quantity, K_VALUE)));
                sum.fold(stringHash(member(quantity, K_UNIT)));
            }
            case CONDITION -> {
                sum.fold(stringHash(firstCodingCodeBytes(resource.get(K_CODE))));
                sum.fold(stringHash(resource.get(K_ONSET_DATE_TIME)));
            }
            default -> {
            }
        }
        sum.count(type);
    }

    private static JsonValue member(JsonValue object, byte[] name) {
        return object != null && object.isObject() ? object.get(name) : null;
    }

    private static JsonValue firstCodingCodeBytes(JsonValue codeableConcept) {
        JsonValue coding = member(codeableConcept, K_CODING);
        if (coding == null || !coding.isArray()) {
            return null;
        }
        Iterator<JsonValue> it = coding.arrayIterator();
        return it.hasNext() ? member(it.next(), K_CODE) : null;
    }

    // ---------------------------------------------------------------------------------------------------
    // domIndex: DOM (tape) walked with the index-based JsonValue methods; string values are hashed
    // straight from the parser's string buffer, so no JsonValue, Iterator or String is allocated.
    // Every position is type-tested (isObjectAt, isArrayAt, isStringAt, isLongAt, isDoubleAt) before
    // it is read, so a value of an unexpected JSON type folds ABSENT, as in the other modes.
    // ---------------------------------------------------------------------------------------------------

    public void domIndex(byte[] buffer, int length) {
        JsonValue bundle = parser.parse(buffer, length);
        if (!bundle.isObject()) {
            return;
        }
        int entries = bundle.field(bundle.index(), K_ENTRY);
        if (entries < 0 || !bundle.isArrayAt(entries)) {
            return;
        }
        int end = bundle.scopeEnd(entries);
        for (int entry = bundle.firstElement(entries); entry >= 0 && entry < end; entry = bundle.advance(entry)) {
            if (!bundle.isObjectAt(entry)) {
                continue;
            }
            int resource = bundle.field(entry, K_RESOURCE);
            if (resource >= 0 && bundle.isObjectAt(resource)) {
                resourceIndex(bundle, resource);
            }
        }
    }

    private void resourceIndex(JsonValue doc, int resource) {
        int resourceType = doc.field(resource, K_RESOURCE_TYPE);
        int type = typeAt(doc, resourceType);
        sum.fold(stringHashAt(doc, resourceType));
        sum.fold(stringHashAt(doc, doc.field(resource, K_ID)));
        switch (type) {
            case PATIENT -> {
                sum.fold(stringHashAt(doc, doc.field(resource, K_BIRTH_DATE)));
                sum.fold(stringHashAt(doc, doc.field(resource, K_GENDER)));
            }
            case ENCOUNTER -> {
                sum.fold(stringHashAt(doc, memberAt(doc, doc.field(resource, K_PERIOD), K_START)));
                sum.fold(stringHashAt(doc, memberAt(doc, doc.field(resource, K_CLASS), K_CODE)));
            }
            case OBSERVATION -> {
                sum.fold(stringHashAt(doc, firstCodingCodeAt(doc, doc.field(resource, K_CODE))));
                sum.fold(stringHashAt(doc, doc.field(resource, K_EFFECTIVE_DATE_TIME)));
                int quantity = doc.field(resource, K_VALUE_QUANTITY);
                sum.fold(numberHashAt(doc, memberAt(doc, quantity, K_VALUE)));
                sum.fold(stringHashAt(doc, memberAt(doc, quantity, K_UNIT)));
            }
            case CONDITION -> {
                sum.fold(stringHashAt(doc, firstCodingCodeAt(doc, doc.field(resource, K_CODE))));
                sum.fold(stringHashAt(doc, doc.field(resource, K_ONSET_DATE_TIME)));
            }
            default -> {
            }
        }
        sum.count(type);
    }

    private static int typeAt(JsonValue doc, int idx) {
        if (idx < 0) {
            return OTHER;
        }
        // stringEquals() is false for non-strings.
        if (doc.stringEquals(idx, T_PATIENT)) {
            return PATIENT;
        }
        if (doc.stringEquals(idx, T_ENCOUNTER)) {
            return ENCOUNTER;
        }
        if (doc.stringEquals(idx, T_OBSERVATION)) {
            return OBSERVATION;
        }
        if (doc.stringEquals(idx, T_CONDITION)) {
            return CONDITION;
        }
        return OTHER;
    }

    private static int memberAt(JsonValue doc, int object, byte[] name) {
        return object >= 0 && doc.isObjectAt(object) ? doc.field(object, name) : -1;
    }

    private static int firstCodingCodeAt(JsonValue doc, int codeableConcept) {
        int coding = memberAt(doc, codeableConcept, K_CODING);
        if (coding < 0 || !doc.isArrayAt(coding)) {
            return -1;
        }
        return memberAt(doc, doc.firstElement(coding), K_CODE);
    }

    private static long stringHashAt(JsonValue doc, int idx) {
        if (idx < 0 || !doc.isStringAt(idx)) {
            return ABSENT;
        }
        return hashString(doc.stringBytes(), doc.byteOffset(idx), doc.byteLength(idx));
    }

    private static long numberHashAt(JsonValue doc, int idx) {
        if (idx < 0) {
            return ABSENT;
        }
        if (doc.isLongAt(idx)) {
            return hashNumber(doc.longAt(idx));
        }
        if (doc.isDoubleAt(idx)) {
            return hashNumber(doc.doubleAt(idx));
        }
        return ABSENT;
    }

    // ---------------------------------------------------------------------------------------------------
    // onDemand: stage 1 only, then a forward-only cursor (SimdJsonParser.parseOnDemand). Wanted values
    // are decoded, everything else is skipped with skipChild(). Fields arrive in document order, so each
    // resource's value hashes are captured first and folded in the fixed order at the end of the
    // resource. Fields are only decoded once the resourceType says they are needed; fields that come
    // before resourceType are captured for every type (Synthea writes resourceType first).
    //
    // The JSON type of a value is checked with peekType() before choosing the getter, so a value of an
    // unexpected type is skipped and folds ABSENT instead of making the getter throw.
    //
    // Strings and keys are decoded into one scratch buffer of the largest document's length plus
    // OnDemandJsonIterator.STRING_BUFFER_PADDING: no decoded string or number literal is longer than the
    // document, and the padding is the room the string decoder may write past the decoded string.
    // ---------------------------------------------------------------------------------------------------

    private static final int UNKNOWN = -1;

    private static final int C_BIRTH_DATE = 0;
    private static final int C_GENDER = 1;
    private static final int C_PERIOD_START = 2;
    private static final int C_CLASS_CODE = 3;
    private static final int C_CODE = 4;
    private static final int C_EFFECTIVE_DATE_TIME = 5;
    private static final int C_QUANTITY_VALUE = 6;
    private static final int C_QUANTITY_UNIT = 7;
    private static final int C_ONSET_DATE_TIME = 8;

    private final long[] captured = new long[9];
    private byte[] scratch;

    public void onDemand(byte[] buffer, int length) {
        if (scratch == null) {
            scratch = new byte[maxDocumentLength + OnDemandJsonIterator.STRING_BUFFER_PADDING];
        }
        OnDemandJsonIterator it = parser.parseOnDemand(buffer, length);
        if (it.peekType() != ValueType.OBJECT || it.startIteratingRootObject() != IteratorResult.NOT_EMPTY) {
            return;
        }
        do {
            int keyLength = it.getFieldName(scratch);
            it.moveToFieldValue();
            if (keyEquals(keyLength, K_ENTRY) && it.peekType() == ValueType.ARRAY) {
                onDemandEntries(it);
            } else {
                it.skipChild();
            }
        } while (it.nextObjectField());
    }

    private void onDemandEntries(OnDemandJsonIterator it) {
        if (it.startIteratingArray() != IteratorResult.NOT_EMPTY) {
            return;
        }
        do {
            if (it.peekType() == ValueType.OBJECT) {
                onDemandEntry(it);
            } else {
                it.skipChild();
            }
        } while (it.nextArrayElement());
    }

    private void onDemandEntry(OnDemandJsonIterator it) {
        if (it.startIteratingObject() != IteratorResult.NOT_EMPTY) {
            return;
        }
        do {
            int keyLength = it.getFieldName(scratch);
            it.moveToFieldValue();
            if (keyEquals(keyLength, K_RESOURCE) && it.peekType() == ValueType.OBJECT) {
                onDemandResource(it);
            } else {
                it.skipChild();
            }
        } while (it.nextObjectField());
    }

    private void onDemandResource(OnDemandJsonIterator it) {
        Arrays.fill(captured, ABSENT);
        long typeHash = ABSENT;
        long idHash = ABSENT;
        int type = UNKNOWN;
        if (it.startIteratingObject() == IteratorResult.NOT_EMPTY) {
            do {
                int keyLength = it.getFieldName(scratch);
                it.moveToFieldValue();
                if (keyEquals(keyLength, K_RESOURCE_TYPE)) {
                    if (it.peekType() == ValueType.STRING) {
                        int n = it.getString(scratch);
                        typeHash = hashString(scratch, 0, n);
                        type = typeOf(scratch, n);
                    } else {
                        it.skipChild();
                        type = OTHER;
                    }
                } else if (keyEquals(keyLength, K_ID)) {
                    idHash = onDemandString(it);
                } else if (wanted(type, PATIENT) && keyEquals(keyLength, K_BIRTH_DATE)) {
                    captured[C_BIRTH_DATE] = onDemandString(it);
                } else if (wanted(type, PATIENT) && keyEquals(keyLength, K_GENDER)) {
                    captured[C_GENDER] = onDemandString(it);
                } else if (wanted(type, ENCOUNTER) && keyEquals(keyLength, K_PERIOD)) {
                    captured[C_PERIOD_START] = onDemandMemberString(it, K_START);
                } else if (wanted(type, ENCOUNTER) && keyEquals(keyLength, K_CLASS)) {
                    captured[C_CLASS_CODE] = onDemandMemberString(it, K_CODE);
                } else if ((wanted(type, OBSERVATION) || wanted(type, CONDITION)) && keyEquals(keyLength, K_CODE)) {
                    captured[C_CODE] = onDemandFirstCodingCode(it);
                } else if (wanted(type, OBSERVATION) && keyEquals(keyLength, K_EFFECTIVE_DATE_TIME)) {
                    captured[C_EFFECTIVE_DATE_TIME] = onDemandString(it);
                } else if (wanted(type, OBSERVATION) && keyEquals(keyLength, K_VALUE_QUANTITY)) {
                    onDemandQuantity(it);
                } else if (wanted(type, CONDITION) && keyEquals(keyLength, K_ONSET_DATE_TIME)) {
                    captured[C_ONSET_DATE_TIME] = onDemandString(it);
                } else {
                    it.skipChild();
                }
            } while (it.nextObjectField());
        }
        if (type == UNKNOWN) {
            type = OTHER;
        }
        sum.fold(typeHash);
        sum.fold(idHash);
        switch (type) {
            case PATIENT -> {
                sum.fold(captured[C_BIRTH_DATE]);
                sum.fold(captured[C_GENDER]);
            }
            case ENCOUNTER -> {
                sum.fold(captured[C_PERIOD_START]);
                sum.fold(captured[C_CLASS_CODE]);
            }
            case OBSERVATION -> {
                sum.fold(captured[C_CODE]);
                sum.fold(captured[C_EFFECTIVE_DATE_TIME]);
                sum.fold(captured[C_QUANTITY_VALUE]);
                sum.fold(captured[C_QUANTITY_UNIT]);
            }
            case CONDITION -> {
                sum.fold(captured[C_CODE]);
                sum.fold(captured[C_ONSET_DATE_TIME]);
            }
            default -> {
            }
        }
        sum.count(type);
    }

    private static boolean wanted(int type, int candidate) {
        return type == UNKNOWN || type == candidate;
    }

    private boolean keyEquals(int keyLength, byte[] expected) {
        return Arrays.equals(scratch, 0, keyLength, expected, 0, expected.length);
    }

    private static int typeOf(byte[] bytes, int length) {
        if (Arrays.equals(bytes, 0, length, T_PATIENT, 0, T_PATIENT.length)) {
            return PATIENT;
        }
        if (Arrays.equals(bytes, 0, length, T_ENCOUNTER, 0, T_ENCOUNTER.length)) {
            return ENCOUNTER;
        }
        if (Arrays.equals(bytes, 0, length, T_OBSERVATION, 0, T_OBSERVATION.length)) {
            return OBSERVATION;
        }
        if (Arrays.equals(bytes, 0, length, T_CONDITION, 0, T_CONDITION.length)) {
            return CONDITION;
        }
        return OTHER;
    }

    /** Cursor at a value: its string hash, or ABSENT (value skipped) if it is not a string. */
    private long onDemandString(OnDemandJsonIterator it) {
        if (it.peekType() != ValueType.STRING) {
            it.skipChild();
            return ABSENT;
        }
        int n = it.getString(scratch);
        return hashString(scratch, 0, n);
    }

    /** Cursor at a value: its number hash, or ABSENT (value skipped) if it is not a number. */
    private long onDemandNumber(OnDemandJsonIterator it) {
        if (it.peekType() != ValueType.NUMBER) {
            it.skipChild();
            return ABSENT;
        }
        int n = it.getRawNumber(scratch);
        return hashNumber(parseNumber(scratch, n));
    }

    /** Cursor at a value: hash of string member {@code name} if the value is an object, else ABSENT. */
    private long onDemandMemberString(OnDemandJsonIterator it, byte[] name) {
        if (it.peekType() != ValueType.OBJECT) {
            it.skipChild();
            return ABSENT;
        }
        long hash = ABSENT;
        if (it.startIteratingObject() != IteratorResult.NOT_EMPTY) {
            return hash;
        }
        do {
            int keyLength = it.getFieldName(scratch);
            it.moveToFieldValue();
            if (keyEquals(keyLength, name)) {
                hash = onDemandString(it);
            } else {
                it.skipChild();
            }
        } while (it.nextObjectField());
        return hash;
    }

    /** Cursor at a CodeableConcept value: hash of coding[0].code, or ABSENT. */
    private long onDemandFirstCodingCode(OnDemandJsonIterator it) {
        if (it.peekType() != ValueType.OBJECT) {
            it.skipChild();
            return ABSENT;
        }
        long hash = ABSENT;
        if (it.startIteratingObject() != IteratorResult.NOT_EMPTY) {
            return hash;
        }
        do {
            int keyLength = it.getFieldName(scratch);
            it.moveToFieldValue();
            if (keyEquals(keyLength, K_CODING) && it.peekType() == ValueType.ARRAY) {
                if (it.startIteratingArray() == IteratorResult.NOT_EMPTY) {
                    boolean first = true;
                    do {
                        if (first) {
                            first = false;
                            hash = onDemandMemberString(it, K_CODE);
                        } else {
                            it.skipChild();
                        }
                    } while (it.nextArrayElement());
                }
            } else {
                it.skipChild();
            }
        } while (it.nextObjectField());
        return hash;
    }

    /** Cursor at a valueQuantity value: captures value (number) and unit (string). */
    private void onDemandQuantity(OnDemandJsonIterator it) {
        if (it.peekType() != ValueType.OBJECT) {
            it.skipChild();
            return;
        }
        if (it.startIteratingObject() != IteratorResult.NOT_EMPTY) {
            return;
        }
        do {
            int keyLength = it.getFieldName(scratch);
            it.moveToFieldValue();
            if (keyEquals(keyLength, K_VALUE)) {
                captured[C_QUANTITY_VALUE] = onDemandNumber(it);
            } else if (keyEquals(keyLength, K_UNIT)) {
                captured[C_QUANTITY_UNIT] = onDemandString(it);
            } else {
                it.skipChild();
            }
        } while (it.nextObjectField());
    }

    private static final double[] POWERS_OF_TEN = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    /**
     * Converts a JSON number literal (as copied by getRawNumber) to the nearest double, without
     * allocating in the common case. With at most 15 significant digits, no exponent and at most 22
     * fraction digits, the digits form an exact double and so does 10^k, and one IEEE division rounds
     * correctly (Clinger's fast path). Anything else goes through Double.parseDouble.
     */
    static double parseNumber(byte[] raw, int length) {
        int i = 0;
        boolean negative = length > 0 && raw[0] == '-';
        if (negative) {
            i++;
        }
        long digits = 0;
        int significant = 0;
        int fractionDigits = 0;
        boolean inFraction = false;
        for (; i < length; i++) {
            byte b = raw[i];
            if (b >= '0' && b <= '9') {
                digits = digits * 10 + (b - '0');
                if (digits != 0) {
                    significant++;
                }
                if (inFraction) {
                    fractionDigits++;
                }
                if (significant > 15) {
                    return Double.parseDouble(new String(raw, 0, length, US_ASCII));
                }
            } else if (b == '.' && !inFraction) {
                inFraction = true;
            } else {
                return Double.parseDouble(new String(raw, 0, length, US_ASCII));
            }
        }
        if (fractionDigits >= POWERS_OF_TEN.length) {
            return Double.parseDouble(new String(raw, 0, length, US_ASCII));
        }
        double value = (double) digits / POWERS_OF_TEN[fractionDigits];
        return negative ? -value : value;
    }
}
