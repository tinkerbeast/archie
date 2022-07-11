package com.github.tinkerbeast.archie.ds;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class LruCache<K, V> implements Map<K,V> {

    class Pair {
        K first;
        V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }
    };

    private final int capacity_;
    private int count_;
    private LinkedList<Pair> store_;
    private HashMap<K, Integer> keyToIdx_;
    java.util.PriorityQueue<Pair> usage_;

    public LruCache(int capacity) {
        this.capacity_ = capacity;
        this.count_ = 0;
        this.store_ = new LinkedList<>();
        this.keyToIdx_ = new HashMap<>();
    }

    @Override
    public void clear() {
        store_.clear();
        count_ = 0;        
    }

    @Override
    public boolean containsKey(Object key) {        
        return keyToIdx_.containsKey(key);
    }

    @Override
    public boolean containsValue(Object arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }

    @Override
    public java.util.Set<Map.Entry<K,V>> entrySet() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }

    @Override
    public V get(Object k) {
        K key = (K)k;
        Integer idx = keyToIdx_.get(key);
        if (idx != null) {
            Pair entry = store_.remove((int)idx); // TODO: **This dramatically reduces performace**
            store_.addFirst(entry);
            keyToIdx_.put(key, Integer.valueOf(0)); // TODO: how to handle this cast?
            return store_.getFirst().second;
        } else {
            return null;
        }
    }

    @Override
    public boolean isEmpty() {        
        return count_ == 0;
    }

    @Override
    public java.util.Set<K> keySet() {        
        return keyToIdx_.keySet();
    }

    @Override
    public V put(K key, V value) {
        Integer idx = keyToIdx_.get(key);
        if (idx == null) {
            if (count_ < capacity_) {
                store_.addFirst(new Pair(key, value));
                ++count_;
            } else {
                Pair entry = store_.removeLast();
                keyToIdx_.remove(entry.first);
                store_.addFirst(new Pair(key, value));
            }
        } else {
            Pair entry = store_.remove((int)idx); // TODO: **This dramatically reduces performace**
            keyToIdx_.remove(entry.first);
            store_.addFirst(new Pair(key, value));
        }
        
        return null;
    }

    @Override
    public void putAll(java.util.Map<? extends K, ? extends V> arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }

    @Override
    public V remove(Object key) {
        Integer idx = keyToIdx_.get(key);
        if (idx == null) {
            return null;
        } else {
            --count_;
            return store_.remove((int)idx).second; // TODO: **This dramatically reduces performace**
        }        
    }

    @Override
    public int size() {        
        return count_;
    }

    @Override
    public Collection<V> values() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }

}
