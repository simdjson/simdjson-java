package org.simdjson;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

class ClassResolver {

    private final Map<Type, ResolvedClass> classCache = new HashMap<>();

    ResolvedClass resolveClass(Type type) {
        ResolvedClass resolvedClass = classCache.get(type);
        if (resolvedClass != null) {
            return resolvedClass;
        }
        resolvedClass = new ResolvedClass(type, this);
        classCache.put(type, resolvedClass);
        return resolvedClass;
    }

    void reset() {
        classCache.clear();
    }
}
