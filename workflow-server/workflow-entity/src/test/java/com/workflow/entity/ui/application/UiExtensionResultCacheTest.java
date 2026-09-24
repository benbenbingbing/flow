package com.workflow.entity.ui.application;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiExtensionResultCacheTest {
    @Test
    void joinedCallerHonorsParentDeadlineWithoutCancellingSharedLoad() throws Exception {
        var cache = new UiExtensionResultCache(2, 100, 1);
        var executor = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var owner = executor.submit(() -> cache.coalesce("key", () -> {
                entered.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                return "shared result";
            }));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            try (var deadline = com.workflow.core.concurrent.ExecutionDeadline.afterMillis(100);
                    var scope = deadline.attach()) {
                assertThrows(TimeoutException.class, () -> cache.coalesce("key", () -> {
                    throw new AssertionError("等待方不能重复执行加载");
                }));
            }
            assertFalse(owner.isDone(), "等待方超时不能取消共享加载");
            release.countDown();
            assertEquals("shared result", owner.get(1, TimeUnit.SECONDS));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void reclaimsUnvisitedExpiredEntriesAndBoundsWeightAndLruCount() {
        var now = new AtomicLong();
        var cache = new UiExtensionResultCache(2, 100, 2, now::get);
        cache.put("a", "first", 1, 40);
        cache.put("b", "second", 20, 40);
        assertEquals("first", cache.get("a").value());
        cache.put("c", "third", 20, 40);
        assertNull(cache.get("b"), "最近未使用的条目先淘汰");
        assertEquals(2, cache.size());
        now.set(TimeUnit.SECONDS.toNanos(2));
        cache.cleanUp();
        assertEquals(1, cache.size(), "过期键无需再次命中即可释放");
        assertEquals(40, cache.weight());
        cache.put("large", "large", 60, 101);
        assertNull(cache.get("large"));
        cache.put("d", null, 20, 70);
        assertNull(cache.get("c"));
        assertNotNull(cache.get("d"), "合法 null 值与缓存未命中不同");
        assertNull(cache.get("d").value());
    }

    @Test
    void coalescesSameKeyAndRejectsExcessDistinctLoadsWithoutLeakingFailures() throws Exception {
        var cache = new UiExtensionResultCache(2, 100, 1);
        var executor = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var owner = executor.submit(() -> cache.coalesce("key", () -> {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return "result";
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var joined = executor.submit(() -> cache.coalesce("key", () -> {
                throw new AssertionError("同键不能重复加载");
            }));
            assertThrows(TimeoutException.class, () -> joined.get(100, TimeUnit.MILLISECONDS));
            assertThrows(RejectedExecutionException.class, () -> cache.coalesce("other", () -> "other"));
            release.countDown();
            assertEquals("result", owner.get(5, TimeUnit.SECONDS));
            assertEquals("result", joined.get(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> cache.coalesce("failure", () -> { throw new IllegalStateException(); }));
            assertEquals("retry", cache.coalesce("failure", () -> "retry"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
