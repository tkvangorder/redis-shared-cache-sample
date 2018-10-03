package com.example.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.Assert;

import com.example.cache.config.CacheSettings;
import com.example.cache.config.CacheSettings.Redis;

import io.micrometer.core.instrument.Tag;


public class UnifiedRedisCacheManager extends AbstractTransactionSupportingCacheManager {

	private final CacheMetricsRegistrar cacheRegistrar;
	
	private final RedisOperations<? extends Object, ? extends Object> redisOperations;
	private final String applicationVersion;

	//Should key values be prefixed with their cache name?
	private final boolean useCacheNamePrefix;
	private final StringRedisSerializer cacheNamePrefixSerializer = new StringRedisSerializer();

	//If using cache name prefixes, the delimiter is the string that separates the cahce name from the actual key value.
	private final String keyDelimiter;

	//Allow caches to be added dynamically
	private boolean dynamic = true;

	//List of caches to pre-create.
	private Set<String> configuredCacheNames; 

	//The default time to live is 1 day.
	private final long defaultTimeToLive;

	//List of specific cache TTL overrides.
	private Map<String, Long> expires = null;

	public UnifiedRedisCacheManager(RedisOperations<? extends Object, ? extends Object> redisOperations, CacheMetricsRegistrar registrar, CacheSettings cacheSettings,
			String applicationVersion) {

		Redis redisProperties = cacheSettings.getRedis();
		this.cacheRegistrar = registrar;
		this.redisOperations = redisOperations;
		this.applicationVersion = applicationVersion;

		if (redisProperties.getTimeToLive() != null) {
			defaultTimeToLive = redisProperties.getTimeToLive().getSeconds();
		} else {
			//If not defined, we default to 1 day.
			defaultTimeToLive = 86400;
		}

		//Should the key values be prefixed with the cache name?
		if (!redisProperties.isUseKeyPrefix()) {
			useCacheNamePrefix = false;
			keyDelimiter = null;
		} else {
			//If so, the format will be <CACHE_NAME><DELIMITER><KEY_VALUE>
			useCacheNamePrefix = true;
			if (StringUtils.isBlank(redisProperties.getKeyPrefix())) {
				keyDelimiter = ":";
			} else {
				keyDelimiter = redisProperties.getKeyPrefix();
			}
		}
		expires = cacheSettings.getExpirations();
	}

	protected UnifiedRedisCache createCache(String cacheName) {
	
		long expiration = computeExpiration(cacheName);
		return new UnifiedRedisCache(cacheName, useCacheNamePrefix?computeCacheNamePrefix(cacheName):null, redisOperations, expiration,
			applicationVersion);
	}

	private byte[] computeCacheNamePrefix(String cacheName) {
		return cacheNamePrefixSerializer.serialize(cacheName.concat(keyDelimiter));
	}

	protected long computeExpiration(String name) {
		Long expiration = null;
		if (expires != null) {
			expiration = expires.get(name);
		}
		return (expiration != null ? expiration.longValue() : defaultTimeToLive);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.support.AbstractCacheManager#loadCaches()
	 */
	@Override
	protected Collection<? extends Cache> loadCaches() {


		if (this.configuredCacheNames == null) {
			return Collections.emptySet();
		}
		Assert.notNull(this.redisOperations, "A redis template is required in order to interact with data store");

		Set<Cache> caches = new HashSet<>();

		for (String cacheName : this.configuredCacheNames) {
			caches.add(createCache(cacheName));
		}
		return caches;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.support.AbstractCacheManager#getMissingCache(java.lang.String)
	 */
	@Override
	protected Cache getMissingCache(String name) {
		if (!this.dynamic) {
			return null;
		}
		Tag cacheManagerTag = Tag.of("cacheManager", "unifiedRedisCacheManager");

		UnifiedRedisCache cache = createCache(name); 
		cacheRegistrar.bindCacheToRegistry(cache, cacheManagerTag);
		return cache;
	}

	/* (non-Javadoc)
	* @see
	org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager#decorateCache(org.springframework.cache.Cache)
	*/
	@Override
	protected Cache decorateCache(Cache cache) {

		if (isCacheAlreadyDecorated(cache)) {
			return cache;
		}

		return super.decorateCache(cache);
	}

	protected boolean isCacheAlreadyDecorated(Cache cache) {
		return isTransactionAware() && cache instanceof TransactionAwareCacheDecorator;
	}
}
