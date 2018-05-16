package com.example.cache.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache")
public class CacheSettings {

	private Map<String, Long> expirations;

	public Map<String, Long> getExpirations() {
		return expirations;
	}

	public void setExpirations(Map<String, Long> expirations) {
		this.expirations = expirations;
	}

}
