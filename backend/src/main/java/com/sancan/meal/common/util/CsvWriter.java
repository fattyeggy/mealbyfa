package com.sancan.meal.common.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 极简 CSV 生成：带 UTF-8 BOM（Excel 打开中文不乱码），
 * 字段包含逗号、引号或换行时按 RFC 4180 转义。
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static byte[] toCsv(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendRow(sb, header);
        for (List<Object> row : rows) {
            appendRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i) == null ? "" : String.valueOf(cells.get(i))));
        }
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
