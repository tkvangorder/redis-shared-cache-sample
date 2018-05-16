package com.example.cache;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.serializer.support.SerializationFailedException;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * This type resolver is responsible for encoding/decoding the object's serialVersionUID into the type stored in the json.
 * The deserialization of an object in redis will fail with an exception if the serialVersionUID of the cached object does not match
 * that of the on in the class loader.
 */
public class RedisJsonTypeIdResolver extends ClassNameIdResolver {

	private static Map<Class<?>, Long> serVerUidMap = new ConcurrentHashMap<>();

	public RedisJsonTypeIdResolver(JavaType baseType, TypeFactory typeFactory) {
		super(baseType, typeFactory);
	}

	@Override
	public String idFromValue(Object value) {
		String id = super.idFromValue(value);
		return appendSerialVersionUid(id, value.getClass());
	}

	@Override
	public String idFromValueAndType(Object value, Class<?> type) {
		String id = super.idFromValueAndType(value, type);
		return appendSerialVersionUid(id, type);
	}

	private String appendSerialVersionUid(String id, Class<?> type) {
		long serVerUid = getSerialVersionUid(type);
		StringBuilder buff = new StringBuilder(id);
		buff.append('%').append(serVerUid);
		return buff.toString();
	}

	private long getSerialVersionUid(Class<?> clazz) {
		if (!Serializable.class.isAssignableFrom(clazz)) {
			throw new SerializationFailedException(clazz.getCanonicalName() + " is not serializable");
		}

		return serVerUidMap.computeIfAbsent(clazz, c -> ObjectStreamClass.lookup(c).getSerialVersionUID());
	}

	@Override
	public JavaType typeFromId(DatabindContext context, String id) throws IOException {
		//This method will extract the serialVersionUID from the cached object.
		String className = id.substring(0, id.lastIndexOf('%'));
		long idSerVerUid = Long.parseLong(id.substring(id.lastIndexOf('%') + 1), 10);
		JavaType javaType = null;
		try {
			//Map the string class name to a Java type.
			javaType = super.typeFromId(context, className);
		} catch (IllegalArgumentException e) {
			throw new SerializationFailedException("InvalidClassException", new InvalidClassException("class not found"));
		}
		//It will determine the Java classes serialVersionUID
		Class<?> rawClass = javaType.getRawClass();
		long typeSerVerUid = getSerialVersionUid(rawClass);
		if (idSerVerUid != typeSerVerUid) {
			//And the cached version does not match the one in memory, we throw an exception.
			throw new SerializationFailedException("SerialVersionMismatch",
					new CachedSerialiVersionMisMatch(rawClass.getName(), idSerVerUid, typeSerVerUid));
		}
		return javaType;
	}
}
