package com.build.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NONE)
public class RedisJsonHolder {

	/**
	 * Wrapped value.
	 */
	@JsonProperty("v")
	private Object value;

	public RedisJsonHolder() {

	}

	public RedisJsonHolder(final Object value) {
		this.value = value;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}
}
