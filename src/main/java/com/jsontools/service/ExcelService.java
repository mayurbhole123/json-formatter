package com.jsontools.service;

import com.jsontools.service.FlattenService.Cell;
import com.jsontools.service.FlattenService.Table;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Builds an .xlsx workbook from a flattened table. */
@Service
public class ExcelService {

    /** Excel's own limit; beyond this the file simply will not open. */
    private static final int MAX_ROWS = 1_048_575;
    private static final int MAX_COLUMNS = 16_384;

    public byte[] toWorkbook(Table table, String sheetName) throws IOException {
        if (table.headers().size() > MAX_COLUMNS) {
            throw new IllegalArgumentException(
                    "The document flattens to " + table.headers().size()
                            + " columns, more than Excel's limit of " + MAX_COLUMNS + ".");
        }
        if (table.rows().size() > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "The document has " + table.rows().size()
                            + " rows, more than Excel's limit of " + MAX_ROWS + ".");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(safeSheetName(sheetName));
            XSSFCellStyle headerStyle = headerStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < table.headers().size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(c);
                cell.setCellValue(table.headers().get(c));
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < table.rows().size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Cell> cells = table.rows().get(r);
                for (int c = 0; c < cells.size(); c++) {
                    writeCell(row.createCell(c), cells.get(c));
                }
            }

            // Keep the header visible when scrolling, and size the columns to fit.
            sheet.createFreezePane(0, 1);
            for (int c = 0; c < table.headers().size(); c++) {
                sheet.autoSizeColumn(c);
                int width = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(Math.max(width + 512, 2560), 16000));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeCell(org.apache.poi.ss.usermodel.Cell cell, Cell value) {
        switch (value.type()) {
            case NULL -> cell.setBlank();
            case BOOLEAN -> cell.setCellValue(Boolean.parseBoolean(value.text()));
            case NUMBER -> {
                try {
                    cell.setCellValue(Double.parseDouble(value.text()));
                } catch (NumberFormatException e) {
                    // Numbers too large for a double stay readable as text.
                    cell.setCellValue(value.text());
                }
            }
            case STRING -> cell.setCellValue(trimToExcelLimit(value.text()));
        }
    }

    /** A single cell cannot hold more than 32767 characters. */
    private String trimToExcelLimit(String text) {
        return text.length() <= 32767 ? text : text.substring(0, 32764) + "...";
    }

    private XSSFCellStyle headerStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    /** Excel rejects [ ] * ? / \ : in sheet names, and caps them at 31 characters. */
    static String safeSheetName(String requested) {
        String name = (requested == null || requested.isBlank()) ? "Sheet1" : requested.trim();
        name = name.replaceAll("[\\[\\]*?/\\\\:]", "_");
        if (name.length() > 31) {
            name = name.substring(0, 31);
        }
        return name.isBlank() ? "Sheet1" : name;
    }
}
