package com.example.cache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.assertj.core.util.Sets;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;

import com.example.cache.CacheHelper;
import com.example.cache.CacheHelperImpl;
import com.example.cache.ExtendedCache;

import org.springframework.cache.CacheManager;

public class CacheHelperTest {


	public static <K>  Matcher<Map<? extends K, ?>> emptyMap() {
		return not(hasKey(any(Object.class)));
	}
	private static final List<String> KEY_LIST = Arrays.asList(
			UUID.randomUUID().toString(),
			UUID.randomUUID().toString(),
			UUID.randomUUID().toString()
	);

	private static final Set<String> KEYS_SET = Collections.unmodifiableSet(Sets.newHashSet(KEY_LIST));

	@Mock
	private Cache cache;

	private CacheManager cacheManager;

	private CacheHelperImpl buildCacheHelper;

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
		cacheManager = Mockito.mock(CacheManager.class);
		buildCacheHelper = new CacheHelperImpl(cacheManager);
	}

	@Test
	public void testGetCache_noCacheManager() {
		assertThat(runNoManagerTest(CacheHelper::getCache), nullValue());
	}

	@Test
	public void testGetCache_noCache() {
		assertThat(runHasCacheManagerTest(CacheHelper::getCache), nullValue());
	}

	@Test
	public void testGetCache_cacheExists() {
		initCache();
		assertThat(runHasCacheManagerTest(CacheHelper::getCache), sameInstance(cache));
	}

	@Test
	public void testGetAll_noCacheManager() {
		assertThat(runNoManagerTest((h, c) -> h.getAll(c, KEYS_SET)), emptyMap());
	}

	@Test
	public void testGetAll_noCache() {
		assertThat(runHasCacheManagerTest((h, c) -> h.getAll(c, KEYS_SET)), emptyMap());
	}

	@Test
	public void testGetAll_noValues() {
		initCache();
		assertThat(runHasCacheManagerTest((h, c) -> h.getAll(c, KEYS_SET)), emptyMap());
		for (String key : KEYS_SET) {
			verify(cache).get(key);
		}
		verifyNoMoreInteractions(cacheManager, cache);
	}

	@Test
	public void testGetAll_mixedValues() {
		initCache();
		ValueWrapper wrapper1 = Mockito.mock(ValueWrapper.class);
		ValueWrapper wrapper2 = Mockito.mock(ValueWrapper.class);


		doReturn(wrapper1).when(cache).get(KEY_LIST.get(0));
		doReturn(wrapper2).when(cache).get(KEY_LIST.get(2));

		Map<String, ValueWrapper> result = runHasCacheManagerTest((h, c) -> h.getAll(c, KEYS_SET));
		for (String key : KEYS_SET) {
			verify(cache).get(key);
		}
		verifyNoMoreInteractions(cacheManager, cache);

		assertThat(result, notNullValue());
		assertThat(result.size(), equalTo(2));
		assertThat(result, hasEntry(KEY_LIST.get(0), wrapper1));
		assertThat(result, hasEntry(KEY_LIST.get(2), wrapper2));
	}

	@Test
	public void testEvictAll_noCacheManager() {
		runNoManagerSinkTest((h, c) -> h.evictAll(c, KEYS_SET));
	}

	@Test
	public void testEvictAll_cacheDoesNotExist() {
		runHasCacheManagerSinkTest((h, c) -> buildCacheHelper.evictAll(c, KEYS_SET));
	}

	@Test
	public void testEvictAll_regularCacheExists() {
		initCache();
		buildCacheHelper.evictAll("testCache", KEYS_SET);
		verify(cacheManager).getCache("testCache");
		for (String key : KEYS_SET) {
			verify(cache).evict(key);
		}
		verifyNoMoreInteractions(cacheManager, cache);
	}

	@Test
	public void testEvictAll_extendedCacheExists() {
		ExtendedCache cache = initCache(Mockito.mock(ExtendedCache.class));
		buildCacheHelper.evictAll("testCache", KEYS_SET);
		verify(cacheManager).getCache("testCache");
		verify(cache).evictAll(KEYS_SET);
		verifyNoMoreInteractions(cacheManager, cache);
	}

	private void initCache() {
		initCache(cache);
	}

	private <T extends Cache> T initCache(T cache) {
		doReturn(cache).when(cacheManager).getCache(anyString());
		return cache;
	}

	private void runNoManagerSinkTest(BiConsumer<CacheHelper, String> action) {
		runNoManagerTest((h, c) -> {
			action.accept(h, c);
			return null;
		});
	}
	private void runHasCacheManagerSinkTest(BiConsumer<CacheHelper, String> action) {
		runHasCacheManagerTest((h, c) -> {
			action.accept(h, c);
			return null;
		});
	}

	private <T> T runNoManagerTest(BiFunction<CacheHelper, String, T> action) {
		buildCacheHelper = new CacheHelperImpl(null);
		T result = action.apply(buildCacheHelper, UUID.randomUUID().toString());
		verifyNoMoreInteractions(cacheManager);
		return result;
	}

	private <T> T runHasCacheManagerTest(BiFunction<CacheHelper, String, T> action) {
		String key = UUID.randomUUID().toString();
		T result = action.apply(buildCacheHelper, key);
		verify(cacheManager).getCache(key);
		verifyNoMoreInteractions(cacheManager);
		return result;
	}
}
