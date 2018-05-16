package com.example.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * This object mapper is used to serialize/deserialize objects to/from Redis. The strategy used by this mapper encodes both the type (class name) along
 * with the type's serialVersionUID. The serialization will fail if a cached object's serialVersionUid (or any of its nested objects' serialVersionUid) does
 * NOT match that of the serialVerionUid in memory. This mapper has been configured to bubble up those serialization exceptions to the caller (Redis Client)
 * which are then caught in the "UnifiedRedisCache". See the overridden logic within the Cache.get() method.
 * 
 * This specialized mapper is installed into the RedisOperations within the "CacheAutoConfiguration" class.
 * 
 * NOTE: This mapper is NOT registered as a Spring bean because we do not want to inadvertently inject it to other places where we do JSON serialization.
 * 
 * NOTE: This mapper will recursively serialize/deserialize child objects and a mismatched UID in a child object will
 *       also cause deserialization to fail. 
 */
public class RedisObjectMapper extends ObjectMapper {

	private static final long serialVersionUID = 1L;

	public RedisObjectMapper() {
		super();

		//Install the "type" resolver that examines serialVersionUids.
		RedisJsonTypeResolverBuilder typer = new RedisJsonTypeResolverBuilder();
		typer.init(JsonTypeInfo.Id.CLASS, null);
		typer.inclusion(JsonTypeInfo.As.WRAPPER_ARRAY);
		setDefaultTyping(typer);

		//We want serialization exceptions to bubble up to the caller.
		configure(DeserializationFeature.WRAP_EXCEPTIONS, false);
		configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
	}
}
