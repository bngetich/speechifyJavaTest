package com.speechify;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConcurrentLRUCache<T> implements LRUCache<T> {

    private final Map<String, T> map;
    
    ConcurrentLRUCache(int capacity) {
        map = new LinkedHashMap<>(capacity, 0.75f , true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, T> eldest) {
                return size() > capacity;
            }
        };
    }
    @Override
    public synchronized T get(String key) {
        return map.get(key);
    }

    @Override
    public synchronized void set(String key, T value) {
        map.put(key, value);
    }
    

}
