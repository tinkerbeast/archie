package com.github.tinkerbeast.archie.ds;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ComputableMap<K, V> implements Map<K, V> {

    Function<K, V> mapper_;

    public ComputableMap(Function<K, V> mapper) {
        mapper_ = mapper;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public boolean containsKey(Object key) {        
        V value = mapper_.apply((K)key);
        return value != null;
    }

    @Override
    public boolean containsValue(Object arg0) {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public V get(Object key) {        
        V value = mapper_.apply((K)key);
        return value;
    }

    @Override
    public boolean isEmpty() {        
        return mapper_ == null;
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public V put(K arg0, V arg1) {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> arg0) {
        throw new UnsupportedOperationException("Operation is meaningless for this class");        
    }

    @Override
    public V remove(Object arg0) {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException("Operation is meaningless for this class");
    }
    
}
