package com.speechify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Concurrent LRU Cache using ReadWriteLock.
 * Allows multiple threads to read simultaneously while ensuring exclusive write access.
 * Optimal for read-heavy workloads (typical cache usage pattern).
 */
public class ConcurrentLRUCache<T> implements LRUCache<T> {

    private final Map<String, T> map;
    private final ReadWriteLock lock;
    
    ConcurrentLRUCache(int capacity) {
        // Initialize the LRU cache using LinkedHashMap with access-order
        map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, T> eldest) {
                return size() > capacity;
            }
        };
        
        // Initialize the read-write lock for concurrent access
        lock = new ReentrantReadWriteLock();
    }
    
    @Override
    public T get(String key) {
        // Acquire read lock (multiple threads can hold this simultaneously)
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            // Always unlock in finally block to prevent deadlocks
            lock.readLock().unlock();
        }
    }

    @Override
    public void set(String key, T value) {
        // Acquire write lock (exclusive - only one thread can hold this)
        lock.writeLock().lock();
        try {
            map.put(key, value);
        } finally {
            // Always unlock in finally block to prevent deadlocks
            lock.writeLock().unlock();
        }
    }
}