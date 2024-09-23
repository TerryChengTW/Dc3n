package com.exchange.utils;

import jxl.Workbook;
import jxl.write.*;
import jxl.write.Number;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderProcessingTracker {
    private static final ConcurrentHashMap<String, Long> processingStartTimes = new ConcurrentHashMap<>();
    private static final List<OrderProcessingData> processingDataList = new ArrayList<>();
    private static final AtomicInteger processedOrderCount = new AtomicInteger(0);
    private static boolean enableBatchSave = true; // 控制是否啟用批量寫入
    private static final int BATCH_SIZE = 250;  // 每處理 250 筆訂單後記錄一次

    // 開始追蹤訂單處理時間
    public static void startTracking(String orderId) {
        processingStartTimes.put(orderId, System.nanoTime());
    }

    // 結束追蹤並計算總處理時間，記錄數據
    public static void endTracking(String orderId, OrderProcessingData data) {
        Long startTime = processingStartTimes.remove(orderId);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;  // 這裡的 duration 不再使用覆蓋 TotalProcessingTime
            // data.setTotalProcessingTime(duration); // 刪除這行，保留 matchOrder 中計算的 TotalProcessingTime
            processingDataList.add(data);
        }

        int count = processedOrderCount.incrementAndGet();

        // 每到達批次量後，將結果寫入 Excel
        if (enableBatchSave && count % BATCH_SIZE == 0) {
            saveToExcel("order_processing_times.xls");
        }
    }

    // 保存處理時間到 Excel (使用 JXL)
    private static void saveToExcel(String filePath) {
        WritableWorkbook workbook = null;
        try {
            // 創建 Excel 文件
            workbook = Workbook.createWorkbook(new File(filePath));
            WritableSheet sheet = workbook.createSheet("Order Processing Times", 0);
            // 創建表頭
            sheet.addCell(new Label(0, 0, "Order ID"));
            sheet.addCell(new Label(1, 0, "Redis Fetch Time (ns)"));
            sheet.addCell(new Label(2, 0, "Trade Update Time (ns)"));
            sheet.addCell(new Label(3, 0, "Redis Update Time (ns)"));
            sheet.addCell(new Label(4, 0, "Get Order From Redis Time (ns)"));
            sheet.addCell(new Label(5, 0, "Add Order To Orderbook Time (ns)"));
            sheet.addCell(new Label(6, 0, "BigDecimal Operation Time (ns)"));
            sheet.addCell(new Label(7, 0, "Object Creation Time (ns)"));
            sheet.addCell(new Label(8, 0, "Total Processing Time (s)"));
            sheet.addCell(new Label(9, 0, "Untracked Time (ns)"));

            // 寫入數據
            int rowIndex = 1;
            for (OrderProcessingData data : processingDataList) {
                sheet.addCell(new Label(0, rowIndex, data.getOrderId()));
                sheet.addCell(new Number(1, rowIndex, data.getRedisFetchTime()));
                sheet.addCell(new Number(2, rowIndex, data.getTradeUpdateTime()));
                sheet.addCell(new Number(3, rowIndex, data.getRedisUpdateTime()));
                sheet.addCell(new Number(4, rowIndex, data.getGetOrderFromRedisTime()));
                sheet.addCell(new Number(5, rowIndex, data.getAddOrderToOrderbookTime()));
                sheet.addCell(new Number(6, rowIndex, data.getBigDecimalOperationTime()));
                sheet.addCell(new Number(7, rowIndex, data.getObjectCreationTime()));
                double totalProcessingTimeInSeconds = data.getTotalProcessingTime() / 1000000000.0;
                sheet.addCell(new Number(8, rowIndex, totalProcessingTimeInSeconds));
                sheet.addCell(new Number(9, rowIndex, data.getUntrackedTime()));
                rowIndex++;
            }

            // 寫入並關閉
            workbook.write();
            System.out.println("Excel file has been updated with order processing times.");

        } catch (IOException | WriteException e) {
            e.printStackTrace();
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException | WriteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
