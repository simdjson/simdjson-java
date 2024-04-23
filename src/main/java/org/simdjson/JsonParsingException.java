package org.simdjson;

public class JsonParsingException extends RuntimeException {

    JsonParsingException(String message) {
        super(message);
    }

    JsonParsingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
