package com.speechify;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleLRUCache<T> implements LRUCache<T> {

    Map<String, T> map;

    SimpleLRUCache(int capacity){
        map = new LinkedHashMap<String, T>(capacity, 0.75f, true){
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, T> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public T get(String key) {
        return map.get(key);
    }

    @Override
    public void set(String key, T value) {
        map.put(key, value);
    }

}
