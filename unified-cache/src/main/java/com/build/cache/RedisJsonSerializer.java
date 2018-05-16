package com.build.cache;

import java.io.IOException;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class RedisJsonSerializer implements RedisSerializer<Object> {

	private final RedisObjectMapper mapper = new RedisObjectMapper();

	@Override
	public byte[] serialize(Object source) throws SerializationException {
		if (source == null || source.equals(SimpleKey.EMPTY)) {
			return new byte[0];
		}

		try {
			return mapper.writeValueAsBytes(source);
		} catch (JsonProcessingException e) {
			throw new SerializationException("Could not write JSON: " + e.getMessage(), e);
		}
	}

	@Override
	public Object deserialize(byte[] source) throws SerializationException {

		if (ArrayUtils.isEmpty(source)) {
			return null;
		}

		try {
			return mapper.readValue(source, Object.class);
		} catch (JsonMappingException e) {
			throw new SerializationException("Error converting JSON byte[] to object", e);
		} catch (JsonParseException e) {
			throw new SerializationException("Could not read JSON: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new SerializationException("IOException reading JSON: " + e.getMessage(), e);
		}
	}

}
