package com.build.cache;

import java.util.Collection;

import org.springframework.cache.Cache;

public interface ExtendedCache extends Cache {
	<T, C extends Collection<T>> void evictAll(C keys);
}
