package com.example.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;

/**
 * The cache helper can be used to perform null-safe operations on the underlying spring caching abstraction.
 */
public interface CacheHelper {

	boolean isCacheEnabled(String cacheName);

	Cache getCache(String cacheName);

	<K> Map<K, ValueWrapper> getAll(String cacheName, Set<K> keySet);

	<K> ValueWrapper get(String cacheName, K key);
	
	/**
	 * Helper method to evict a specific value from the cache.
	 * 
	 * @param cacheName Name of the cache
	 * @param key Key for the value that will be evicted.
	 */
	void evict(String cacheName, Object key);

	/**
	 * Helper method to evict a specific set of values from the cache.
	 *
	 * @param cacheName Name of the cache
	 * @param keys the keys to evict
	 */
	<T, C extends Collection<T>> void evictAll(String cacheName, C keys);

	/**
	 * Helper method to first evict any existing value within the cache associated with the key and then set the new
	 * value.
	 * 
	 * @param cacheName Name of the cache
	 * @param key Key within the cache
	 * @param value Value to be cached.
	 */
	void put(String cacheName, Object key, Object value, boolean evict);

	/**
	 * Helper method to add a set of key/values to a given cache. There is also a flag indicating if those values should
	 * first be evicted prior to being set on the cache.
	 * 
	 * @param cacheName Name of the cache
	 * @param cacheEntries The set of key/values that will be added the cache. 
	 * @param ev
	 */

	<K, V> void putAll(String cacheName, Map<K, V> cacheEntries, boolean evict);
}
