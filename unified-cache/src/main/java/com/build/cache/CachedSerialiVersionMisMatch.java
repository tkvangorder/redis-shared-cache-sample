package com.build.cache;

/**
 * This exception is thrown when the json serializer encounters a cached object that has a different
 * serialversionUID than the one in the current class loader.
 */
public class CachedSerialiVersionMisMatch extends Exception {
	private static final long serialVersionUID = 1L;
	
	public CachedSerialiVersionMisMatch(String className, long cachedVersionUid, long currentVersionUid) {
		super("Class [" + className + "] : Cached Version [ " + cachedVersionUid + "], Current Version [" + currentVersionUid + "]");
	}

}
