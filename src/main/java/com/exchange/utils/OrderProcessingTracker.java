package com.exchange.utils;

import java.util.concurrent.ConcurrentHashMap;

public class OrderProcessingTracker {
    private static final ConcurrentHashMap<String, Long> processingStartTimes = new ConcurrentHashMap<>();

    public static void startTracking(String orderId) {
        processingStartTimes.put(orderId, System.nanoTime());
    }

    public static long endTracking(String orderId) {
        Long startTime = processingStartTimes.remove(orderId);
        if (startTime == null) {
            return -1; // 表示沒有找到開始時間
        }
        return System.nanoTime() - startTime;
    }
}