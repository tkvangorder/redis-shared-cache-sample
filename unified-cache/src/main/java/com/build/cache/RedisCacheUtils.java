package com.build.cache;

import java.util.Arrays;

import org.springframework.data.redis.core.RedisOperations;

public final class RedisCacheUtils {

	private RedisCacheUtils() {
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static byte[] computeKey(RedisOperations template, byte[] prefix, Object key) {
		if (template.getKeySerializer() == null && key instanceof byte[]) {
			return (byte[]) key;
		}
		byte[] k = template.getKeySerializer().serialize(key);

		if (prefix == null || prefix.length == 0) {
			return k;
		}

		byte[] result = Arrays.copyOf(prefix, prefix.length + k.length);
		System.arraycopy(k, 0, result, prefix.length, k.length);
		return result;
	}

}
