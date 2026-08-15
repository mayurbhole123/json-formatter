package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns an arbitrary JSON document into a rectangular table. Shared by the CSV,
 * TSV, Excel and SQL converters so all four agree on columns and cell text.
 */
@Service
public class FlattenService {

    /** What the cell originally was, so SQL can skip quoting and Excel can write real numbers. */
    public enum CellType { STRING, NUMBER, BOOLEAN, NULL }

    public record Cell(String text, CellType type) {

        public static final Cell NULL = new Cell("", CellType.NULL);

        public static Cell text(String value) {
            return new Cell(value, CellType.STRING);
        }
    }

    /** A rectangular view of a document: a header row plus typed data rows. */
    public record Table(List<String> headers, List<List<Cell>> rows) {

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        /** The same rows as plain strings, for the CSV/TSV writers. */
        public List<List<String>> textRows() {
            List<List<String>> out = new ArrayList<>(rows.size());
            for (List<Cell> row : rows) {
                List<String> cells = new ArrayList<>(row.size());
                for (Cell cell : row) {
                    cells.add(cell.text());
                }
                out.add(cells);
            }
            return out;
        }
    }

    /**
     * Chooses the records to turn into rows.
     * <ul>
     *   <li>an array is its own record list;</li>
     *   <li>an object wrapping a single array (the common {@code {"data":[...]}} shape)
     *       unwraps to that array;</li>
     *   <li>anything else is a single record.</li>
     * </ul>
     */
    public List<JsonNode> records(JsonNode root) {
        if (root.isArray()) {
            List<JsonNode> out = new ArrayList<>();
            root.forEach(out::add);
            return out;
        }
        if (root.isObject() && root.size() == 1) {
            JsonNode only = root.properties().iterator().next().getValue();
            if (only.isArray()) {
                List<JsonNode> out = new ArrayList<>();
                only.forEach(out::add);
                return out;
            }
        }
        return List.of(root);
    }

    public Table toTable(JsonNode root, boolean flattenNested) {
        List<JsonNode> records = records(root);
        List<Map<String, Cell>> mapped = new ArrayList<>(records.size());
        LinkedHashSet<String> headers = new LinkedHashSet<>();

        for (JsonNode record : records) {
            Map<String, Cell> row = new LinkedHashMap<>();
            if (record.isObject()) {
                for (Map.Entry<String, JsonNode> field : record.properties()) {
                    collect(field.getKey(), field.getValue(), row, flattenNested);
                }
            } else if (record.isArray() && flattenNested) {
                for (int i = 0; i < record.size(); i++) {
                    collect(String.valueOf(i), record.get(i), row, true);
                }
            } else {
                row.put("value", cellOf(record));
            }
            headers.addAll(row.keySet());
            mapped.add(row);
        }

        List<String> headerList = new ArrayList<>(headers);
        List<List<Cell>> rows = new ArrayList<>(mapped.size());
        for (Map<String, Cell> row : mapped) {
            List<Cell> cells = new ArrayList<>(headerList.size());
            for (String header : headerList) {
                // A key absent from this record is a genuine NULL, not an empty string.
                cells.add(row.getOrDefault(header, Cell.NULL));
            }
            rows.add(cells);
        }
        return new Table(headerList, rows);
    }

    /** Walks one value into the row, expanding containers into dotted column names. */
    private void collect(String path, JsonNode value, Map<String, Cell> row, boolean flattenNested) {
        if (value.isObject()) {
            if (!flattenNested || value.isEmpty()) {
                row.put(path, value.isEmpty() ? Cell.text("") : Cell.text(value.toString()));
                return;
            }
            for (Map.Entry<String, JsonNode> field : value.properties()) {
                collect(path + "." + field.getKey(), field.getValue(), row, true);
            }
            return;
        }
        if (value.isArray()) {
            if (!flattenNested || value.isEmpty()) {
                row.put(path, value.isEmpty() ? Cell.text("") : Cell.text(value.toString()));
                return;
            }
            // An array of scalars reads far better joined than split over columns.
            if (isScalarArray(value)) {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < value.size(); i++) {
                    if (i > 0) {
                        joined.append(", ");
                    }
                    joined.append(scalarText(value.get(i)));
                }
                row.put(path, Cell.text(joined.toString()));
                return;
            }
            for (int i = 0; i < value.size(); i++) {
                collect(path + "." + i, value.get(i), row, true);
            }
            return;
        }
        row.put(path, cellOf(value));
    }

    private Cell cellOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Cell.NULL;
        }
        if (node.isNumber()) {
            return new Cell(node.asText(), CellType.NUMBER);
        }
        if (node.isBoolean()) {
            return new Cell(node.asText(), CellType.BOOLEAN);
        }
        return Cell.text(scalarText(node));
    }

    private boolean isScalarArray(JsonNode array) {
        for (JsonNode child : array) {
            if (child.isContainerNode()) {
                return false;
            }
        }
        return true;
    }

    /** Cell text for a scalar: strings unquoted, nulls blank, numbers verbatim. */
    public String scalarText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
