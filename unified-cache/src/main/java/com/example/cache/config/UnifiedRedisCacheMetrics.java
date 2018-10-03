package com.example.cache.config;

import com.example.cache.UnifiedRedisCache;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;

public class UnifiedRedisCacheMetrics extends CacheMeterBinder {

	private final UnifiedRedisCache cache;
	
    public UnifiedRedisCacheMetrics(UnifiedRedisCache cache, String cacheName, Iterable<Tag> tags) {
        super(cache, cacheName, tags);
        this.cache = cache;
    }
    
	@Override
	protected Long size() {
		//There is no good way to get the size of the cache, short of adding a specialized key/value to redis.
		return null;
	}

	@Override
	protected long hitCount() {
		return cache.getHitCount();
	}

	@Override
	protected Long missCount() {
		return cache.getMissCount();
	}

	@Override
	protected Long evictionCount() {
		// There is no good way to get the eviction count from Redis (that I am aware of)
		return null;
	}

	@Override
	protected long putCount() {
		return cache.getPutCount();
	}

	@Override
	protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.promotions", cache,
                c -> {
                    Long promotions = cache.getPromotionCount();
                    return promotions == null ? 0 : promotions;
                })
                .tags(getTagsWithCacheName()).tag("result", "promotion")
                .description("the number of times a cached value has been promoted from an earlier version of the application.")
                .register(registry);
	}

}
