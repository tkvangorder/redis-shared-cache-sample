package com.example.cache;

import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisOperations;

public class LooseRedisCacheManager extends RedisCacheManager {


	private final CounterService counterService;
	private final RedisOperations<? extends Object, ? extends Object> redisOperations;
	private final String applicationVersion;
	public LooseRedisCacheManager(RedisOperations<? extends Object, ? extends Object> redisOperations, CounterService counterService, String applicationVersion) {
		super(redisOperations);
		this.redisOperations = redisOperations;
		this.counterService = counterService;
		this.applicationVersion = applicationVersion;
	}

	protected RedisCache createCache(String cacheName) {
		long expiration = computeExpiration(cacheName);
		
		return new LooseRedisCache(cacheName, (isUsePrefix() ? getCachePrefix().prefix(cacheName) : null), redisOperations, expiration,
			counterService, applicationVersion);
	}
	
}
