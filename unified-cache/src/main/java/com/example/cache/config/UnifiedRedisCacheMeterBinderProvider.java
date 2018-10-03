package com.example.cache.config;

import org.springframework.boot.actuate.metrics.cache.CacheMeterBinderProvider;

import com.example.cache.UnifiedRedisCache;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

public class UnifiedRedisCacheMeterBinderProvider implements CacheMeterBinderProvider<UnifiedRedisCache> {

	@Override
	public MeterBinder getMeterBinder(UnifiedRedisCache cache, Iterable<Tag> tags) {
		return new UnifiedRedisCacheMetrics(cache, cache.getName(), tags);
	}
}
