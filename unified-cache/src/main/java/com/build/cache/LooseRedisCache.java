package com.build.cache;

import java.util.Collection;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.TooManyClusterRedirectionsException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * This cache uses a "unified caching model" which allows multiple versions of an application to share the same redis instance. Cached
 * values are stored by cache name to a key. The value is actually a hashset of application version to serialized value. This means that
 * the same object can be cached more than once if their are two different versions of the application that both leverage the cache.
 * 
 * The BuildCacheHelper provides convenience methods for explicitly working with the unified cache, specifically the "put" operation in
 * the helper provides an optional flag that can be used to evict Other versions of a cached object.
 * 
 * <PRE>
 * Example: 
 * 
 * Application Instance 1 has a version of 876.
 * Application Instance 2 has a version of 877.
 * 
 * They both want to cache an article by the article ID.
 * 
 * If instance 1 does a "get" for article 13 with caching:  
 * 
 * Redis
 *   | - articleCache
 *           |--Key = 13 Value (Key=876, Value = "{json encoded stuff serialversionUID 1}")
 *
 * If instance 2 does a "get" for article 13 with caching, the "get" operation in the cache will "promote" version 876's copy to version 877.
 * Promotion will ONLY happen if the serialVersionUID of version 1 matches that of version 2. This means that no changes have been made to the model.
 * 
 * Redis
 *   | - articleCache
 *           |--Key = 13 Value (Key=876, Value = "{json encoded stuff serialVersionUID 1}")
 *                       Value (Key=877, Value = "{json encoded stuff serialVersionUID 1}") <-- promoted if the serialVersionUid hasnt changed.
 *                       
 * If the serialVersionUid in the cached version is DIFFERENT from that of version 877, that indicates the model has changed. This results in a cache "miss".
 * Version 877, will hit the underlying database....and then cache its value 
 *
 * Redis
 *   | - articleCache
 *           |--Key = 13 Value (Key=876, Value = "{json encoded stuff version 1}") <-- Instance 1 has its own "legacy" copy.
 *                       Value (Key=877, Value = "{json encoded stuff version 2}") <-- Instance 2 has its own copy of the "same" object because the model changed.
 * 
 *  If instance 1 issues an evict on article 13, it will result in ALL instances having their cache cleared: 
 *  
 * Redis
 *   | - articleCache
 *           | -- Empty.
 *  
 * There places within the application that do an explicit "put" that should also "clear" other cached versions in Redis. 
 * 
 * If instance 1 issues a put + evict to update article 13, it will clear ANY other version from the cache as well. 
 * 
 * Redis
 *   | - articleCache
 *           |--Key = 13 Value (Key=876, Value = "{json encoded stuff version 1}")
 *</PRE>
 *
 */
public class LooseRedisCache extends RedisCache implements ExtendedCache {

	private Log log = LogFactory.getLog(LooseRedisCache.class);

	private final CounterService counterService;
	private final String counterPrefix;
	private final RedisSerializer<String> versionSerializer;
	private final String applicationVersion;
	private final long expiration;
	private final byte[] prefix;
	
	/**
	 * Constructs a new <code>LooseRedisCache</code> instance.
	 *
	 * @param name cache name
	 * @param prefix
	 * @param template
	 * @param expiration
	 */
	public LooseRedisCache(String name, byte[] prefix, RedisOperations<? extends Object, ? extends Object> redisOperations, long expiration, CounterService counterService, String applicationVersion) {

		super(name, prefix, redisOperations, expiration);
		this.versionSerializer = new StringRedisSerializer();		
		this.prefix = prefix;
		this.expiration = expiration;
		this.counterService = counterService;
		this.counterPrefix = "counter.cache." + name;
		this.applicationVersion = applicationVersion;
	}

	@Override
	public ValueWrapper get(final Object key) {
		counterService.increment(counterPrefix + ".get");
		ValueWrapper valueWrapper;
		final byte[] keyBytes = RedisCacheUtils.computeKey(getRedisOperations(), prefix, key);
		Integer appVerNum = null;
		try {
			appVerNum = Integer.parseInt(applicationVersion);
		} catch (NumberFormatException e) {
			// ignore
		}
		final byte[] versionBytes = versionSerializer.serialize(applicationVersion);
		final Integer fAppVerNum = appVerNum;
		
		try {
			RedisOperations<? extends Object, ? extends Object> redisOperations = getRedisOperations();
			valueWrapper = (ValueWrapper) redisOperations.execute(new RedisCallback<ValueWrapper>() {

				@Override
				public ValueWrapper doInRedis(RedisConnection connection) throws DataAccessException {
					byte[] bs = connection.hGet(keyBytes, versionBytes);
					Object value = redisOperations.getValueSerializer() != null ? redisOperations.getValueSerializer().deserialize(bs) : bs;
					if (bs == null && fAppVerNum != null) {
						Set<byte[]> hKeys = connection.hKeys(keyBytes);
						Integer maxVersion = null;
						for (byte[] hKey : hKeys) {
							String version = (String) versionSerializer.deserialize(hKey);
							try {
								int verNum = Integer.parseInt(version);
								if (maxVersion == null || verNum > maxVersion) {
									maxVersion = verNum;
								}
							} catch (NumberFormatException e) {
								// ignore
							}
						}
						if (maxVersion != null) {
							bs = connection.hGet(keyBytes, versionSerializer.serialize("" + maxVersion));
							// bs could be null if evicted after the version scan
							if (bs != null) {
								value = redisOperations.getValueSerializer() != null ? redisOperations.getValueSerializer().deserialize(bs) : bs;
								connection.hSet(keyBytes, versionBytes, bs);
								counterService.increment(counterPrefix + ".get.promote");
							}
						}
					}
					return bs == null ? null : new SimpleValueWrapper(value);
				}
			});
		} catch (RedisConnectionFailureException|TooManyClusterRedirectionsException|InvalidDataAccessApiUsageException e) {
			counterService.increment(counterPrefix + ".get.error");
			log.trace("Redis exception. Falling back to regular DB access.", e);
			return null;
		} catch (SerializationFailedException | SerializationException exception) {
			counterService.increment(counterPrefix + ".get.error.serialization");
			log.trace("Redis serialization exception: " + exception.getMessage(), exception);
			return null;
		}
		if (valueWrapper == null) {
			counterService.increment(counterPrefix + ".get.miss");
			log.trace("cache: " + getName() + " - cache miss for key " + key);
			return null;
		} else {
			counterService.increment(counterPrefix + ".get.hit");
			log.trace("cache: " + getName() + " - cache hit for key " + key);
			return valueWrapper;
		}
	}

	@Override
	public void put(final Object key, final Object value) {
		try {
			final byte[] keyBytes = RedisCacheUtils.computeKey(getRedisOperations(), prefix, key);
			final byte[] valueBytes = convertToBytesIfNecessary(getRedisOperations().getValueSerializer(), value);			
			final byte[] versionBytes = versionSerializer.serialize(applicationVersion);
			
			getRedisOperations().execute(new RedisCallback<Object>() {

				@Override
				public Object doInRedis(RedisConnection connection) throws DataAccessException {
					connection.hSet(keyBytes, versionBytes, valueBytes);
					if (expiration > 0) {
						connection.expire(keyBytes, expiration);
					}
					return null;
				}
			});
			counterService.increment(counterPrefix + ".put");
		} catch (RedisConnectionFailureException|TooManyClusterRedirectionsException|InvalidDataAccessApiUsageException e) {
			log.trace("Redis exception. Cache puts are non-critical.", e);
			counterService.increment(counterPrefix + ".put.error");
		}
	}

	@Override
	public <T, C extends Collection<T>> void evictAll(C keys) {
		byte[][] keyBytes = new byte[keys.size()][];

		int i = 0;

		for (Object key : keys) {
			keyBytes[i] = RedisCacheUtils.computeKey(getRedisOperations(), prefix, key);
			i++;
		}

		getRedisOperations().execute(new RedisCallback<Object>() {
			@Override
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				connection.del(keyBytes);
				return null;
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.Cache#putIfAbsent(java.lang.Object, java.lang.Object)
	 */
	public ValueWrapper putIfAbsent(Object key, final Object value) {
		//Really dont need this semantic for our use case, we just call the standard put and return the value we are setting.
		put(key, value);
		return new SimpleValueWrapper(value);
	}
	
	

	@SuppressWarnings({"unchecked", "rawtypes"})
	private byte[] convertToBytesIfNecessary(RedisSerializer redisSerializer, Object value) {

		if (redisSerializer == null) {
			if (value instanceof byte[]) {
				return (byte[]) value;
			} else {
				throw new RuntimeException("serializer required");
			}
		}

		return redisSerializer.serialize(value);
	}
	
	
	@SuppressWarnings({"unchecked"})
	private RedisOperations<? extends Object, ? extends Object> getRedisOperations() {
		return (RedisOperations<? extends Object, ? extends Object>)getNativeCache();
	}
}
