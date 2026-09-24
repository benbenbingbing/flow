package com.workflow.entity.ui.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 扩展结果的进程内有界缓存。LRU、权重和 TTL 共同限制驻留量；
 * 同一个授权上下文键只保留一次加载，失败不缓存，也不让不同键无限堆积等待对象。
 */
final class UiExtensionResultCache {
    private final int maxEntries;
    private final long maxWeight;
    private final int maxLoads;
    private final LongSupplier clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, CompletableFuture<Object>> loads = new LinkedHashMap<>();
    private long weight;

    UiExtensionResultCache(int maxEntries, long maxWeight, int maxLoads) {
        this(maxEntries, maxWeight, maxLoads, System::nanoTime);
    }

    UiExtensionResultCache(int maxEntries, long maxWeight, int maxLoads, LongSupplier clock) {
        if (maxEntries < 1 || maxWeight < 1 || maxLoads < 1) throw new IllegalArgumentException("缓存上限必须大于零");
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
        this.maxLoads = maxLoads;
        this.clock = clock;
    }

    /** Hit 包装允许缓存合法的 null 结果，不能将 null 结果误判为未命中。 */
    synchronized Hit get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAt <= clock.getAsLong()) {
            entries.remove(key);
            weight -= entry.weight;
            return null;
        }
        return new Hit(entry.value);
    }

    /** 超大结果直接跳过缓存；entryWeight 是包含对象膨胀余量的序列化体积估算。 */
    synchronized void put(String key, Object value, int seconds, long entryWeight) {
        if (seconds <= 0 || entryWeight > maxWeight) return;
        cleanUp();
        Entry old = entries.remove(key);
        if (old != null) weight -= old.weight;
        long boundedWeight = Math.max(1, entryWeight);
        // 执行策略可能来自旧快照，缓存最长驻留一天，使用单调时钟避免系统校时影响过期。
        long expires = clock.getAsLong() + TimeUnit.SECONDS.toNanos(Math.min(86_400, seconds));
        entries.put(key, new Entry(value, expires, boundedWeight));
        weight += boundedWeight;
        var iterator = entries.entrySet().iterator();
        while (entries.size() > maxEntries || weight > maxWeight) {
            weight -= iterator.next().getValue().weight;
            iterator.remove();
        }
    }

    /** 定时主动清理，即使某个用户/参数组合再也不被访问，其过期值也会释放。 */
    synchronized void cleanUp() {
        long now = clock.getAsLong();
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.expiresAt <= now) {
                weight -= entry.weight;
                iterator.remove();
            }
        }
    }

    /** 加载在锁外执行；调用方负责对真实 Provider 设置执行截止时间。 */
    Object coalesce(String key, Callable<Object> loader) throws Exception {
        CompletableFuture<Object> shared;
        boolean owner;
        synchronized (this) {
            shared = loads.get(key);
            owner = shared == null;
            if (owner) {
                if (loads.size() >= maxLoads) throw new RejectedExecutionException("接口扩展并发加载已达上限");
                shared = new CompletableFuture<>();
                loads.put(key, shared);
            }
        }
        if (!owner) {
            // 合并加载不能放宽当前调用的父预算；等待方超时只结束自身等待，不取消其他请求共享的加载。
            var deadline = com.workflow.core.concurrent.ExecutionDeadline.current();
            return deadline == null ? shared.get() : shared.get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        }
        try {
            Object result = loader.call();
            shared.complete(result);
            return result;
        } catch (Throwable failure) {
            shared.completeExceptionally(failure);
            if (failure instanceof Exception exception) throw exception;
            throw (Error) failure;
        } finally {
            synchronized (this) {
                loads.remove(key, shared);
            }
        }
    }

    synchronized int size() { return entries.size(); }
    synchronized long weight() { return weight; }
    record Hit(Object value) {}
    private record Entry(Object value, long expiresAt, long weight) {}
}
