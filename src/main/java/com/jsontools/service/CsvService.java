package com.jsontools.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained RFC 4180 CSV/TSV reader and writer.
 * Handles quoted fields, doubled quotes inside quotes, and embedded newlines.
 */
@Service
public class CsvService {

    public static char delimiterOf(String name) {
        if (name == null) return ',';
        String v = name.trim().toLowerCase();
        if ("tab".equals(v) || "\\t".equals(v) || "tsv".equals(v)) return '\t';
        if ("semicolon".equals(v) || ";".equals(v)) return ';';
        if ("pipe".equals(v) || "|".equals(v)) return '|';
        if ("comma".equals(v) || ",".equals(v)) return ',';
        return v.isEmpty() ? ',' : v.charAt(0);
    }

    /** Splits delimited text into rows of raw string cells. */
    public List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<List<String>>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean cellWasQuoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');   // escaped quote
                        i++;
                    } else {
                        inQuotes = false;   // closing quote
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }

            if (c == '"' && cell.length() == 0) {
                inQuotes = true;
                cellWasQuoted = true;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
            } else if (c == '\r') {
                // swallow; the \n that follows ends the record
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
                rows.add(row);
                row = new ArrayList<String>();
            } else {
                cell.append(c);
            }
        }

        // Trailing record (no newline at end of file)
        if (cell.length() > 0 || cellWasQuoted || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }

        // Drop a single trailing blank line
        if (!rows.isEmpty()) {
            List<String> last = rows.get(rows.size() - 1);
            if (last.size() == 1 && last.get(0).isEmpty()) {
                rows.remove(rows.size() - 1);
            }
        }
        return rows;
    }

    /** Renders rows back to delimited text, quoting only where required. */
    public String write(List<List<String>> rows, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) sb.append(delimiter);
                sb.append(escape(row.get(c), delimiter));
            }
            if (r < rows.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public String escape(String value, char delimiter) {
        if (value == null) return "";
        boolean needsQuotes = value.indexOf(delimiter) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
