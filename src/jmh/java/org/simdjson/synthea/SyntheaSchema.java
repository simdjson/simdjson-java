package org.simdjson.synthea;

import org.simdjson.annotations.JsonFieldName;

import java.util.List;

/**
 * Records for schema-based parsing ({@code SimdJsonParser.parse(buffer, len, Bundle.class)}).
 * <p>
 * Schema-based parsing maps one Java type per JSON field name, so {@link Resource} is the union of the
 * fields the extraction needs from every resource type. Fields not declared here are skipped by the parser.
 */
final class SyntheaSchema {

    private SyntheaSchema() {
    }

    record Bundle(List<Entry> entry) {
    }

    record Entry(Resource resource) {
    }

    record Resource(
            String resourceType,
            String id,
            String birthDate,
            String gender,
            Period period,
            @JsonFieldName("class") Coding encounterClass,
            CodeableConcept code,
            String effectiveDateTime,
            Quantity valueQuantity,
            String onsetDateTime) {
    }

    record Period(String start) {
    }

    record Coding(String code) {
    }

    record CodeableConcept(List<Coding> coding) {
    }

    // FHIR Quantity.value is a JSON number that Synthea writes both as integers ("2") and as decimals ("108.1").
    record Quantity(Double value, String unit) {
    }
}
