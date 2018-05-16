package com.example.cache.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class allows each cache's time time live (TTL) to be configured via spring's property sources.
 * Any cache's expiration will be defaulted to 1 day if it is not explicitly defined.
 * 
 * <PRE>
 * The unit of time for TTL is SECONDS:
 *  
 * Simple Reference:
 * 
 *   600     - 10 minutes
 *   3600    - 1 hour
 *   14400   - 4 hours
 *   86400   - 1 day
 *   345600  - 4 days
 *   604800  - 1 week
 *   1209600 - 2 weeks 
 * </PRE>
 * 
 * @author tyler.vangorder
 */
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
