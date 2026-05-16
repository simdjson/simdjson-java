OnDemandSimdJson-java
=====================
[![](https://img.shields.io/badge/License-Apache%202-blue.svg)](LICENSE)

A **path-based**, **on-demand** JSON parser for Java, built on top of
[simdjson-java](https://github.com/simdjson/simdjson-java) - the JVM port of
[simdjson](https://github.com/simdjson/simdjson) (see _Parsing Gigabytes of JSON per Second_,
[Langdale & Lemire, 2019](https://arxiv.org/abs/1902.08318)).

**OnDemandSimdJson-java keeps simdjson-java's SIMD structural indexing stage 1, and replaces stage 2
with a path-driven projector** that pulls only the fields you ask for - never materializing intermediate
`JsonNode`/`JsonObject` trees. This makes it well-suited for large-scale data pipelines where each JSON
document is queried as a fixed **`(path1, path2, ..., pathN) → tuple`** projection.

## Why this fork?

[simdjson-java](https://github.com/simdjson/simdjson-java) already ships two excellent parsers:

| Mode | API | What it does |
|------|-----|---------------|
| DOM | [`SimdJsonParser.parse(buf, len)`](src/main/java/org/simdjson/SimdJsonParser.java) | Builds an in-memory `JsonValue` tree for the whole document. |
| Schema | [`SimdJsonParser.parse(buf, len, Class<T>)`](src/main/java/org/simdjson/SimdJsonParser.java) | Materializes a typed record graph matching `T`. |

Both are fast, but both eagerly build objects for **every** field in the document - even fields you
never touch. In analytics / streaming workloads that often translates to large per-document GC pressure.

OnDemandSimdJson-java adds a third mode purpose-built for **multi-field projection**:

| Mode | API | What it does |
|------|-----|---------------|
| **Paths** | [`PathsBasedJsonParser.parse(buf, len)`](src/main/java/org/simdjson/PathsBasedJsonParser.java) | Returns an `Object[]` containing one value per pre-declared path. No tree is ever built. |

### Highlights

- **JSON-tuple in one pass**: declare your `N` interesting paths up-front; each call to `parse()`
  returns an `Object[N]` with the typed values - similar in spirit to a SQL row projection.
- **Zero intermediate nodes**: the parser only walks the structural indexes for the branches that
  contain a requested path; everything else is `skipChild`-ed at the SIMD level.
- **Early termination per object**: once all requested children of an object have been seen, the
  parser jumps straight to the closing brace.
- **Reuses simdjson-java's stage-1 SIMD indexer**, so structural parsing throughput is unchanged.
- **Strongly-typed values**: per-path type annotations (`:INT`, `:LONG`, `:DOUBLE`, ...) produce
  already-parsed primitives, no boxing-then-`Integer.parseInt(String)` round-trip.

## Code Sample

### Paths-based parser (the new mode)

```java
byte[] json = """
    {
      "user":  {"id": 4242, "name": "alice"},
      "items": [{"price": 19.95}, {"price":  9.50}],
      "extra": "we don't care about this"
    }
    """.getBytes(StandardCharsets.UTF_8);

PathsBasedJsonParser parser = new PathsBasedJsonParser(
        "user.id:LONG",
        "user.name",                  // default type is STRING
        "items.[0].price:DOUBLE"
);

Object[] row = parser.parse(json, json.length);
long   id    = (long)   row[0];   // 4242
String name  = (String) row[1];   // "alice"
double price = (double) row[2];   // 19.95
```

Paths that are absent from the document return `null` in the corresponding slot. The returned array
is freshly allocated, so you may safely keep references across subsequent `parse()` calls.

### Path syntax

| Construct | Meaning |
|-----------|---------|
| `a.b.c` | descend into object keys `a` → `b` → `c` |
| `arr.[3]` | take the 4th element of an array |
| `field:INT` | force the leaf type (default is `STRING`) |
| `[[lit]]` | the literal object key `[lit]` (double `[[` / `]]` escapes the bracket syntax) |

Supported leaf types: `STRING` (default), `INT`, `LONG`, `SHORT`, `BYTE`, `FLOAT`, `DOUBLE`,
`CHAR`, `BOOLEAN` plus their `*_PRIMITIVE` variants (which throw on JSON `null`).

If the leaf path points to a JSON object or array, the parser stores the **raw slice** of the input
buffer (as a `String`), e.g. `"[1,2,3,4]"`. This is handy for delegating sub-tree handling to a
second-stage parser when needed.

### simdjson-java's original APIs are still available

The DOM and schema modes from the upstream project are preserved:

```java
// DOM
JsonValue jv = new SimdJsonParser().parse(json, json.length);

// Schema
record User(boolean default_profile, String screen_name) {}
record Status(User user) {}
record Twitter(List<Status> statuses) {}
Twitter t = new SimdJsonParser().parse(json, json.length, Twitter.class);
```

## When to pick which mode

| Workload | Recommended parser |
|----------|--------------------|
| You need the whole document as a tree | `SimdJsonParser` (DOM) |
| You parse into a fixed record / record-graph | `SimdJsonParser` (schema) |
| You only need a handful of values out of large documents, repeatedly | **`PathsBasedJsonParser`** |
| Streaming a column-store / Spark UDF / Hive SerDe-style projection | **`PathsBasedJsonParser`** |

## Build & Run

Requires **Java 24+** (the upstream project bumped the minimum JDK to 24 in commit `b6cab55`
because [`StructuralIndexer`](src/main/java/org/simdjson/StructuralIndexer.java) /
[`Utf8Validator`](src/main/java/org/simdjson/Utf8Validator.java) now use vector operators
introduced in Java 22+).

```bash
./gradlew test
./gradlew jmh                 # JMH benchmarks
```

On Apple Silicon (ARM) the default `SPECIES_PREFERRED` is 128-bit, which the indexer rejects.
Explicitly pick a vector width when running tests locally:

```bash
./gradlew test -Dorg.simdjson.species=512    # or 256
```

## Known Limitations

- JSON escape sequences (e.g. `\uXXXX`, `\n`) inside string values **and** inside object keys are
  not decoded for the path-based mode - the parser compares / returns raw UTF-8 bytes. Use the DOM
  or schema mode if you need decoded strings.
- A single `PathsBasedJsonParser` instance is **not thread-safe**; create one per worker thread.
- Maximum supported document size is **34 MiB** (the indexer buffer is fixed at construction time).
- The path syntax uses `.`, `:`, and `[N]` as meta-characters; literal `[` / `]` inside object
  keys must be doubled (`[[` / `]]`). Literal `.` or `:` inside keys are not currently representable.

## License & Attribution

Distributed under the Apache 2.0 License - see [LICENSE](LICENSE).

This project is a derivative work of [simdjson-java](https://github.com/simdjson/simdjson-java)
(Apache 2.0). All credit for the SIMD structural indexing implementation goes to the original
authors of [simdjson](https://github.com/simdjson/simdjson) (Geoff Langdale and Daniel Lemire) and
to the maintainers of the JVM port. OnDemandSimdJson-java only contributes the
[`PathsBasedJsonParser`](src/main/java/org/simdjson/PathsBasedJsonParser.java) /
[`OnDemandJsonIterator`](src/main/java/org/simdjson/OnDemandJsonIterator.java) /
[`OnDemandJsonValue`](src/main/java/org/simdjson/OnDemandJsonValue.java) layer on top of that work.
