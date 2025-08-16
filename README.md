simdjson-java
=============
![Build Status](https://github.com/simdjson/simdjson-java/actions/workflows/ci.yml/badge.svg)
[![](https://maven-badges.herokuapp.com/maven-central/org.simdjson/simdjson-java/badge.svg)](https://central.sonatype.com/search?namespace=org.simdjson)
[![](https://img.shields.io/badge/License-Apache%202-blue.svg)](LICENSE)

A Java version of [simdjson](https://github.com/simdjson/simdjson) - a JSON parser using SIMD instructions,
based on the paper [Parsing Gigabytes of JSON per Second](https://arxiv.org/abs/1902.08318) 
by Geoff Langdale and Daniel Lemire.

## Code Sample

### DOM Parser

```java
byte[] json = loadTwitterJson();

SimdJsonParser parser = new SimdJsonParser();
JsonValue jsonValue = parser.parse(json, json.length);
Iterator<JsonValue> tweets = jsonValue.get("statuses").arrayIterator();
while (tweets.hasNext()) {
    JsonValue tweet = tweets.next();
    JsonValue user = tweet.get("user");
    if (user.get("default_profile").asBoolean()) {
        System.out.println(user.get("screen_name").asString());
    }
}
```

### Schema-Based Parser

```java
byte[] json = loadTwitterJson();

SimdJsonParser parser = new SimdJsonParser();
SimdJsonTwitter twitter = simdJsonParser.parse(buffer, buffer.length, SimdJsonTwitter.class);
for (SimdJsonStatus status : twitter.statuses()) {
    SimdJsonUser user = status.user();
    if (user.default_profile()) {
        System.out.println(user.screen_name());
    }
}

record SimdJsonUser(boolean default_profile, String screen_name) {
}

record SimdJsonStatus(SimdJsonUser user) {
}

record SimdJsonTwitter(List<SimdJsonStatus> statuses) {
}
```

## Installation

The library is available in the [Maven Central Repository](https://mvnrepository.com/artifact/org.simdjson/simdjson-java). 
To include it in your project, add the following dependency to the `build.gradle` file:
```groovy
implementation("org.simdjson:simdjson-java:0.1.0")
```

or to the `pom.xml` file:
```xml
<dependency>
    <groupId>org.simdjson</groupId>
    <artifactId>simdjson-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

Please remember about specifying the desired version. 

Note that simdjson-java follows the [SemVer specification](https://semver.org/), which means, for example, that a major 
version of zero indicates initial development, so the library's API should not be considered stable.

We require Java 24 or better.

## Benchmarks

To run the JMH benchmarks, execute the following command:

```./gradlew jmh```

## Tests

To run the tests, execute the following command:

```./gradlew test```

## Performance

This section presents a performance comparison of different JSON parsers available as Java libraries. The benchmark used 
the [twitter.json](src/jmh/resources/twitter.json) dataset, and its goal was to measure the throughput (ops/s) of parsing 
and finding all unique users with a default profile.

### 256-bit Vectors

Environment:
* CPU: Intel(R) Xeon(R) CPU E5-2686 v4 @ 2.30GHz
* OS: Ubuntu 24.04 LTS, kernel 6.8.0-1008-aws
* Java: OpenJDK 64-Bit Server VM (build 21.0.3+9-Ubuntu-1ubuntu1, mixed mode, sharing)

DOM parsers ([ParseAndSelectBenchmark](src/jmh/java/org/simdjson/ParseAndSelectBenchmark.java)):

| Library                                          | Version | Throughput (ops/s) |
|--------------------------------------------------|---------|--------------------|
| simdjson-java (padded)                           | 0.3.0   | 783.878            |
| simdjson-java                                    | 0.3.0   | 760.426            |
| [fastjson2](https://github.com/alibaba/fastjson) | 2.0.49  | 308.660            |
| [jackson](https://github.com/FasterXML/jackson)  | 2.17.0  | 259.536            |

Schema-based parsers ([SchemaBasedParseAndSelectBenchmark](src/jmh/java/org/simdjson/SchemaBasedParseAndSelectBenchmark.java)):

| Library                                                         | Version | Throughput (ops/s) |
|-----------------------------------------------------------------|---------|--------------------|
| simdjson-java (padded)                                          | 0.3.0   | 1237.432           |
| simdjson-java                                                   | 0.3.0   | 1216.891           |
| [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) | 2.28.4  | 614.138            |
| [fastjson2](https://github.com/alibaba/fastjson)                | 2.0.49  | 494.362            |
| [jackson](https://github.com/FasterXML/jackson)                 | 2.17.0  | 339.904            |

### 512-bit Vectors

Environment:
* CPU: Intel(R) Xeon(R) Platinum 8375C CPU @ 2.90GHz
* OS: Ubuntu 24.04 LTS, kernel 6.8.0-1008-aws
* Java: OpenJDK 64-Bit Server VM (build 21.0.3+9-Ubuntu-1ubuntu1, mixed mode, sharing)

DOM parsers ([ParseAndSelectBenchmark](src/jmh/java/org/simdjson/ParseAndSelectBenchmark.java)):

| Library                                          | Version | Throughput (ops/s) |
|--------------------------------------------------|---------|--------------------|
| simdjson-java (padded)                           | 0.3.0   | 1842.146           |
| simdjson-java                                    | 0.3.0   | 1765.592           |
| [fastjson2](https://github.com/alibaba/fastjson) | 2.0.49  | 718.133            |
| [jackson](https://github.com/FasterXML/jackson)  | 2.17.0  | 616.617            |

Schema-based parsers ([SchemaBasedParseAndSelectBenchmark](src/jmh/java/org/simdjson/SchemaBasedParseAndSelectBenchmark.java)):

| Library                                                         | Version | Throughput (ops/s) |
|-----------------------------------------------------------------|---------|--------------------|
| simdjson-java (padded)                                          | 0.3.0   | 3164.274           |
| simdjson-java                                                   | 0.3.0   | 2990.289           |
| [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) | 2.28.4  | 1826.229           |
| [fastjson2](https://github.com/alibaba/fastjson)                | 2.0.49  | 1259.622           |
| [jackson](https://github.com/FasterXML/jackson)                 | 2.17.0  | 789.030            |

To reproduce the benchmark results, execute the following command:

```./gradlew jmh -Pjmh.includes='.*ParseAndSelectBenchmark.*'```

The benchmark may take several minutes. Remember that you need Java 18 or better.
