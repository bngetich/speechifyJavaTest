package com.speechify;

/**
 *
 * Use the provided com.speechify.LRUCacheProviderTest in `src/test/java/LruCacheTest.java` to validate your
 * implementation.
 *
 * You may:
 *  - Read online API references for Java standard library or JVM collections.
 * You must not:
 *  - Read guides about how to code an LRU cache.
 */

public class LRUCacheProvider {
    public static <T> LRUCache<T> createLRUCache(CacheLimits options) {
        int capacity = options.getMaxItemsCount();

        // Factory logic: choose implementation based on capacity
        if(capacity > 100){
            // For larger caches, use thread-safe version
            return new ConcurrentLRUCache<>(capacity);
        }

        // For small caches, use simple version (faster)
        return new SimpleLRUCache<>(capacity);
    }
}
