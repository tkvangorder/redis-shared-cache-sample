package com.example.cache.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.example.cache.BuildCacheHelper;
import com.example.cache.BuildCacheHelperImpl;
import com.example.cache.LooseRedisCacheManager;
import com.example.cache.RedisJsonSerializer;

/**
 * This configuration is automatically added to the spring context when the dependency on this library is added to a project. 
 * The "discovery" of this configuration is via the spring boot mechanism:  src/main/resources/META-INF/spring.factories.
 * 
 * The cache configuration assumes the caching implementation is redis or Nothing. The caching in spring can be disabled by setting
 * the property ("spring.cache.type) to a value of "none".
 * 
 * This configuration will only be enabled with the spring.cache.type is either missing (which then defaults to redis) or explicitly et
 * to redis.
 * 
 * This cache uses a "unified caching model" which allows multiple versions of an application to share the same redis instance. Cached
 * values are stored by cache name to a key. The value is actually a hashset of application version to serialized value. This means that
 * the same object can be cached more than once if their are two different versions of the application that both leverage the cache.
 * 
 * The BuildCacheHelper provides convenience methods for explicitly working with the unified cache, specifically the "put" operation in
 * the helper provides an optional flag that can be used to evict Other versions of a cached object.
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
 */

@Configuration
@EnableCaching
@EnableConfigurationProperties({ CacheSettings.class})
public class CacheAutoConfiguration {

	@Bean
	public BuildCacheHelper buildCacheHelper(CacheManager cacheManager) {
		//Expose an instance of the cache helper. This is especially important in use cases where a "put" operation should also evict all other copies of a
		//given object from cache.
		return new BuildCacheHelperImpl(cacheManager);
	}

	@ConditionalOnExpression("'${spring.cache.type:redis}' == 'redis'")
	protected static class CacheEnabledConfiguration {

		@Bean
		public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
			RedisTemplate<Object, Object> template = new RedisTemplate<>();
			template.setConnectionFactory(connectionFactory);
			RedisJsonSerializer jsonRedisSerializer = new RedisJsonSerializer();
			template.setKeySerializer(jsonRedisSerializer);
			template.setValueSerializer(jsonRedisSerializer);
			return template;
		}

		@Bean(name = {"cacheManager"})
		public CacheManager cacheManager(RedisTemplate<?, ?> redisTemplate, CacheSettings cacheSettings,
				CounterService counterService, @Value("${info.build.version:1.0.0-SNAPSHOT}") String applicationVersion) {
			LooseRedisCacheManager cacheManager = new LooseRedisCacheManager(redisTemplate, counterService, applicationVersion);
			cacheManager.setDefaultExpiration(86400);
			cacheManager.setUsePrefix(true);
			cacheManager.setExpires(cacheSettings.getExpirations());
			return cacheManager;
		}
	}

}