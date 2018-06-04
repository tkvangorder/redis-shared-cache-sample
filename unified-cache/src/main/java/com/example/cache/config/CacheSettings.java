package com.example.cache.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This settings object is meant to mirror the CacheProperties object to insure we are still respecting most of the Redis configuration defined by the core
 * Spring framework.
 * 
 * @author tyler.vangorder
 *
 */
@ConfigurationProperties(prefix = "spring.cache")
public class CacheSettings {

	private Map<String, Long> expirations;

	private final Redis redis = new Redis();
	public Map<String, Long> getExpirations() {
		return expirations;
	}

	public void setExpirations(Map<String, Long> expirations) {
		this.expirations = expirations;
	}

	public Redis getRedis() {
		return this.redis;
	}
	
	/**
	 * Redis-specific cache properties.
	 */
	public static class Redis {

		/**
		 * Entry expiration. By default the entries never expire.
		 */
		private Duration timeToLive;

		/**
		 * Allow caching null values.
		 */
		private boolean cacheNullValues = true;

		/**
		 * Key prefix.
		 */
		private String keyPrefix;

		/**
		 * Whether to use the key prefix when writing to Redis.
		 */
		private boolean useKeyPrefix = true;

		public Duration getTimeToLive() {
			return this.timeToLive;
		}

		public void setTimeToLive(Duration timeToLive) {
			this.timeToLive = timeToLive;
		}

		public boolean isCacheNullValues() {
			return this.cacheNullValues;
		}

		public void setCacheNullValues(boolean cacheNullValues) {
			this.cacheNullValues = cacheNullValues;
		}

		public String getKeyPrefix() {
			return this.keyPrefix;
		}

		public void setKeyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
		}

		public boolean isUseKeyPrefix() {
			return this.useKeyPrefix;
		}

		public void setUseKeyPrefix(boolean useKeyPrefix) {
			this.useKeyPrefix = useKeyPrefix;
		}
	}

}
