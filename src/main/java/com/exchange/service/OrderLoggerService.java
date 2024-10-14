package com.exchange.service;

import com.exchange.model.OrderRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderLoggerService {

    private final List<OrderRecord> orderRecords = new ArrayList<>();
    private final Object lock = new Object();
    private static final String FILE_PATH = "orders.xlsx";

    // 添加訂單到暫存區
    public void addOrderRecord(OrderRecord orderRecord) {
        synchronized (lock) {
            orderRecords.add(orderRecord);
        }
    }

    // 每30秒寫入一次Excel
    @Scheduled(fixedRate = 30000)
    public void writeOrdersToExcel() {
        synchronized (lock) {
            if (!orderRecords.isEmpty()) {
                File file = new File(FILE_PATH);
                Workbook workbook = null;
                FileOutputStream fileOut = null;

                try {
                    // 檢查檔案是否存在
                    if (file.exists()) {
                        FileInputStream fileInputStream = new FileInputStream(file);
                        workbook = new XSSFWorkbook(fileInputStream); // 打開現有檔案
                    } else {
                        workbook = new XSSFWorkbook(); // 創建新檔案
                    }

                    // 檢查是否已經存在工作表，否則創建一個新的工作表
                    Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : workbook.createSheet("Orders");

                    // 如果是新檔案或第一次寫入，創建標題列
                    if (sheet.getLastRowNum() == 0) {
                        Row header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Order ID");
                        header.createCell(1).setCellValue("User ID");
                        header.createCell(2).setCellValue("Symbol");
                        header.createCell(3).setCellValue("Price");
                        header.createCell(4).setCellValue("Quantity");
                        header.createCell(5).setCellValue("Received Time");
                    }

                    // 找到目前已經有的最後一行的行號
                    int rowIndex = sheet.getLastRowNum() + 1;

                    // 將資料填入Excel
                    for (OrderRecord record : orderRecords) {
                        Row row = sheet.createRow(rowIndex++);
                        row.createCell(0).setCellValue(record.getOrder().getId());
                        row.createCell(1).setCellValue(record.getOrder().getUserId());
                        row.createCell(2).setCellValue(record.getOrder().getSymbol());
                        row.createCell(3).setCellValue(record.getOrder().getPrice().toString());
                        row.createCell(4).setCellValue(record.getOrder().getQuantity().toString());
                        row.createCell(5).setCellValue(record.getReceivedTime());
                    }

                    // 寫入到檔案
                    fileOut = new FileOutputStream(FILE_PATH);
                    workbook.write(fileOut);

                    // 清空暫存區
                    orderRecords.clear();

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (workbook != null) {
                            workbook.close();
                        }
                        if (fileOut != null) {
                            fileOut.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
