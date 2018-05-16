package com.example.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;

public class BuildCacheHelperImpl implements BuildCacheHelper {

	private final CacheManager cacheManager;

	public BuildCacheHelperImpl(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@Override
	public Cache getCache(String cacheName) {
		if (cacheManager == null) {
			return null;
		}
		return cacheManager.getCache(cacheName);
	}

	@Override
	public <K> Map<K, ValueWrapper> getAll(String cacheName, Set<K> keySet) {
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return new HashMap<>();
		}
		Map<K, ValueWrapper> results = new HashMap<>();
		for (K key : keySet) {
			ValueWrapper valueWrapper = cache.get(key);
			if (valueWrapper != null) {
				results.put(key, valueWrapper);
			}
		}
		return results;
	}

	@Override
	public <K> ValueWrapper get(String cacheName, K key) {
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return null;
		}
		return cache.get(key);
	}

	@Override
	public void put(String cacheName, Object key, Object value, boolean evict) {
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return;
		}
		if (evict) {
			cache.evict(key);
		}
		cache.put(key, value);
	}

	@Override
	public <K, V> void putAll(String cacheName, Map<K, V> cacheEntries, boolean evict) {
		if (cacheEntries.isEmpty()) {
			return;
		}
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return;
		}
		for (Map.Entry<K, V> entry : cacheEntries.entrySet()) {
			if (evict) {
				cache.evict(entry.getKey());
			}
			cache.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public boolean isCacheEnabled(String cacheName) {
		Cache cache = getCache(cacheName);
		return cache != null && cache.getNativeCache() != null;
	}

	@Override
	public void evict(String cacheName, Object key) {
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return;
		}
		cache.evict(key);
	}

	@Override
	public <T, C extends Collection<T>> void evictAll(String cacheName, C keys) {
		Cache cache = getCache(cacheName);
		if (cache == null) {
			return;
		}
		if (cache instanceof ExtendedCache) {
			((ExtendedCache)cache).evictAll(keys);
			return;
		}
		for (Object key : keys) {
			cache.evict(key);
		}
	}
}
