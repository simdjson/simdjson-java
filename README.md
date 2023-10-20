simdjson-java
=============
![Build Status](https://github.com/simdjson/simdjson-java/actions/workflows/ci.yml/badge.svg)
[![](https://maven-badges.herokuapp.com/maven-central/org.simdjson/simdjson-java/badge.svg)](https://central.sonatype.com/search?namespace=org.simdjson)
[![](https://img.shields.io/badge/License-Apache%202-blue.svg)](LICENSE)

A Java version of [simdjson](https://github.com/simdjson/simdjson) - a JSON parser using SIMD instructions,
based on the paper [Parsing Gigabytes of JSON per Second](https://arxiv.org/abs/1902.08318) 
by Geoff Langdale and Daniel Lemire.

## Code Sample

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

We require Java 18 or better.

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

**Note that simdjson-java is still missing several features (see [GitHub Issues](https://github.com/simdjson/simdjson-java/issues)), 
so the following results may not reflect its real performance.**

Environment:
* CPU: Intel(R) Core(TM) i5-4590 CPU @ 3.30GHz
* OS: Ubuntu 23.04, kernel 6.2.0-23-generic
* Java: OpenJDK 64-Bit Server VM Temurin-20.0.1+9

 Library                                           | Version | Throughput (ops/s) 
---------------------------------------------------|---------|--------------------
 simdjson-java                                     | -       | 1450.951           
 simdjson-java (padded)                            | -       | 1505.227           
 [jackson](https://github.com/FasterXML/jackson)   | 2.15.2  | 504.562            
 [fastjson2](https://github.com/alibaba/fastjson)  | 2.0.35  | 590.743            
 [jsoniter](https://github.com/json-iterator/java) | 0.9.23  | 384.664            

To reproduce the benchmark results, execute the following command:

```./gradlew jmh -Pjmh.includes='.*ParseAndSelectBenchmark.*'```

The benchmark may take several minutes. Remember that you need Java 18 or better.