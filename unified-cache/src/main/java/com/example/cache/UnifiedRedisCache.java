package com.example.cache;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.TooManyClusterRedirectionsException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.DecoratedRedisConnection;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.Assert;

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
public class UnifiedRedisCache implements Cache {

	private Log log = LogFactory.getLog(UnifiedRedisCache.class);

	private final long expiration;
	private final byte[] prefix;
	private final RedisOperations<? extends Object, ? extends Object> redisOperations;
	private final RedisCacheMetadata cacheMetadata;

	final Integer applicationVersion;
	final RedisSerializer<String> versionSerializer;
	private final byte[] currentVersionBytes;

	private long hitCount = 0;
	private long missCount = 0;
	private long putCount = 0;
	private long promotionCount = 0;
	
	/**
	 * Constructs a new <code>UnifiedRedisCache</code> instance.
	 *
	 * @param name cache name
	 * @param prefix
	 * @param template
	 * @param expiration
	 */
	public UnifiedRedisCache(String name, byte[] prefix, RedisOperations<? extends Object, ? extends Object> redisOperations, long expiration, String applicationVersion) {

		Assert.hasText(name, "CacheName must not be null or empty!");
		Assert.notNull(redisOperations.getValueSerializer(), "The Redis template must have a value serializer when using this cache.");

		this.cacheMetadata = new RedisCacheMetadata(name, prefix);
		this.cacheMetadata.setDefaultExpiration(expiration);
		this.redisOperations = redisOperations;

		this.prefix = prefix;
		this.expiration = expiration;

		Integer version = null;
		try {
			version = Integer.parseInt(applicationVersion);
		} catch (NumberFormatException e) {
			//ignore exception, we want this to be null
		}
		this.applicationVersion = version;
		versionSerializer = new StringRedisSerializer();
		currentVersionBytes = versionSerializer.serialize(applicationVersion);
	}

	@Override
	public ValueWrapper get(final Object key) {
		
		ValueWrapper valueWrapper;
		final byte[] keyBytes = RedisCacheUtils.computeKey(redisOperations, prefix, key);

		try {
			valueWrapper = redisOperations.execute((RedisCallback<ValueWrapper>)
				connection -> {
					byte[] bs = connection.hGet(keyBytes, currentVersionBytes);
					Object value = redisOperations.getValueSerializer() != null ? redisOperations.getValueSerializer().deserialize(bs) : bs;
					if (bs == null && applicationVersion != null) {
						Set<byte[]> hKeys = connection.hKeys(keyBytes);
						Integer maxVersion = null;
						for (byte[] hKey : hKeys) {
							String version = versionSerializer.deserialize(hKey);
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
								promotionCount++;
								connection.hSet(keyBytes, currentVersionBytes, bs);
							}
						}
					}
					return bs == null ? null : new SimpleValueWrapper(value);
				});
		} catch (RedisConnectionFailureException|TooManyClusterRedirectionsException|InvalidDataAccessApiUsageException e) {
			log.trace("Redis exception. Falling back to regular DB access.", e);
			return null;
		} catch (SerializationFailedException | SerializationException exception) {
			log.trace("Redis serialization exception: " + exception.getMessage(), exception);
			return null;
		}
		if (valueWrapper == null) {
			missCount++;
			return null;
		} else {
			hitCount++;
			return valueWrapper;
		}
	}

	@Override
	public void put(final Object key, final Object value) {
		try {
			final byte[] keyBytes = RedisCacheUtils.computeKey(redisOperations, prefix, key);
			final byte[] valueBytes = convertToBytesIfNecessary(redisOperations.getValueSerializer(), value);

			redisOperations.execute(new RedisCallback<Object>() {

				@Override
				public Object doInRedis(RedisConnection connection) throws DataAccessException {
					connection.hSet(keyBytes, currentVersionBytes, valueBytes);
					if (expiration > 0) {
						connection.expire(keyBytes, expiration);
					}
					return null;
				}
			});
			putCount++;
		} catch (RedisConnectionFailureException|TooManyClusterRedirectionsException|InvalidDataAccessApiUsageException e) {
			log.trace("Redis exception. Cache puts are non-critical.", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.Cache#putIfAbsent(java.lang.Object, java.lang.Object)
	 */
	@Override
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

	/**
	 * Return the value to which this cache maps the specified key, generically specifying a type that return value will
	 * be cast to.
	 *
	 * @param key
	 * @param type
	 * @return
	 * @see DATAREDIS-243
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Object key, Class<T> type) {

		ValueWrapper wrapper = get(key);
		return wrapper == null ? null : (T) wrapper.get();
	}

	/*
	 * @see  org.springframework.cache.Cache#get(java.lang.Object, java.util.concurrent.Callable)
	 * introduced in springframework 4.3.0.RC1
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(final Object key, final Callable<T> valueLoader) {

		//The recommendation is to attempt to implement this method as an atomic operation, this is not really used in our implementation
		//and this cache takes a naive approach to this contract, it is not guaranteed to be atomic.
		ValueWrapper val = get(key);
		if (val != null) {
			return (T) val.get();
		}

		T value;
		try {
			value = valueLoader.call();
			put(key, value);
			return value;
		} catch (Exception exception) {
			throw new ValueRetrievalException(key, valueLoader, exception);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.Cache#evict(java.lang.Object)
	 */
	@Override
	public void evict(final Object key) {

		redisOperations.execute(new RedisCallback<Object>() {

			@Override
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				final byte[] keyBytes = RedisCacheUtils.computeKey(redisOperations, prefix, key);
				connection.del(keyBytes);
				return null;
			}
		});

	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.Cache#clear()
	 */
	@Override
	public void clear() {
		redisOperations.execute(new RedisCacheCleanByPrefixCallback(cacheMetadata));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.cache.Cache#getName()
	 */
	@Override
	public String getName() {
		return cacheMetadata.getCacheName();
	}

	/**
	 * {@inheritDoc} This implementation simply returns the RedisTemplate used for configuring the cache, giving access to
	 * the underlying Redis store.
	 */
	@Override
	public Object getNativeCache() {
		return redisOperations;
	}

	/**
	 * Metadata required to maintain {@link RedisCache}. Keeps track of additional data structures required for processing
	 * cache operations.
	 *
	 * @author Christoph Strobl
	 * @since 1.5
	 */
	static class RedisCacheMetadata {

		private final String cacheName;
		private final byte[] keyPrefix;
		private long defaultExpiration = 0;

		/**
		 * @param cacheName must not be {@literal null} or empty.
		 * @param keyPrefix can be {@literal null}.
		 */
		public RedisCacheMetadata(String cacheName, byte[] keyPrefix) {

			Assert.hasText(cacheName, "CacheName must not be null or empty!");
			this.cacheName = cacheName;
			this.keyPrefix = keyPrefix;

		}

		/**
		 * @return true if the {@link RedisCache} uses a prefix for key ranges.
		 */
		public boolean usesKeyPrefix() {
			return (keyPrefix != null && keyPrefix.length > 0);
		}

		/**
		 * Get the binary representation of the key prefix.
		 *
		 * @return never {@literal null}.
		 */
		public byte[] getKeyPrefix() {
			return this.keyPrefix;
		}

		/**
		 * Get the name of the cache.
		 *
		 * @return
		 */
		public String getCacheName() {
			return cacheName;
		}

		/**
		 * Set the default expiration time in seconds
		 *
		 * @param seconds
		 */
		public void setDefaultExpiration(long seconds) {
			this.defaultExpiration = seconds;
		}

		/**
		 * Get the default expiration time in seconds.
		 *
		 * @return
		 */
		public long getDefaultExpiration() {
			return defaultExpiration;
		}

	}


	static class RedisCacheCleanByPrefixCallback implements RedisCallback<Void> {

		private static final byte[] REMOVE_KEYS_BY_PATTERN_LUA = new StringRedisSerializer().serialize(
				"local keys = redis.call('KEYS', ARGV[1]); local keysCount = table.getn(keys); if(keysCount > 0) then for _, key in ipairs(keys) do redis.call('del', key); end; end; return keysCount;");
		private static final byte[] WILD_CARD = new StringRedisSerializer().serialize("*");
		private final RedisCacheMetadata metadata;

		public RedisCacheCleanByPrefixCallback(RedisCacheMetadata metadata) {
			this.metadata = metadata;
		}

		@Override
		public Void doInRedis(RedisConnection connection) {
			byte[] prefixToUse = Arrays.copyOf(metadata.getKeyPrefix(), metadata.getKeyPrefix().length + WILD_CARD.length);
			System.arraycopy(WILD_CARD, 0, prefixToUse, metadata.getKeyPrefix().length, WILD_CARD.length);

			if (isClusterConnection(connection)) {

				// load keys to the client because currently Redis Cluster connections do not allow eval of lua scripts.
				Set<byte[]> keys = connection.keys(prefixToUse);
				if (!keys.isEmpty()) {
					connection.del(keys.toArray(new byte[keys.size()][]));
				}
			} else {
				connection.eval(REMOVE_KEYS_BY_PATTERN_LUA, ReturnType.INTEGER, 0, prefixToUse);
			}

			return null;
		}
		private static boolean isClusterConnection(RedisConnection connection) {

			while (connection instanceof DecoratedRedisConnection) {
				connection = ((DecoratedRedisConnection) connection).getDelegate();
			}

			return connection instanceof RedisClusterConnection;
		}

	}


	public long getHitCount() {
		return hitCount;
	}

	public long getMissCount() {
		return missCount;
	}

	public long getPutCount() {
		return putCount;
	}

	public long getPromotionCount() {
		return promotionCount;
	}


}
