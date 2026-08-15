package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jsontools.service.FlattenService.Cell;
import com.jsontools.service.FlattenService.CellType;
import com.jsontools.service.FlattenService.Table;
import org.springframework.stereotype.Service;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** JSON to and from CSV/TSV, XML, HTML tables and SQL. */
@Service
public class ConversionService {

    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final JsonService json;
    private final CsvService csv;
    private final XmlService xml;
    private final FlattenService flatten;

    public ConversionService(JsonService json, CsvService csv, XmlService xml, FlattenService flatten) {
        this.json = json;
        this.csv = csv;
        this.xml = xml;
        this.flatten = flatten;
    }

    // ==================================================================
    // JSON -> CSV / TSV
    // ==================================================================

    public String jsonToDelimited(JsonNode root, char delimiter, boolean header, boolean flattenNested) {
        Table table = flatten.toTable(root, flattenNested);
        if (table.isEmpty()) {
            return "";
        }
        List<List<String>> rows = new ArrayList<>();
        if (header) {
            rows.add(table.headers());
        }
        rows.addAll(table.textRows());
        return csv.write(rows, delimiter);
    }

    // ==================================================================
    // CSV / TSV -> JSON
    // ==================================================================

    public JsonNode delimitedToJson(String text, char delimiter, boolean hasHeader, boolean coerce) {
        List<List<String>> rows = csv.parse(text, delimiter);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No rows found. Check the delimiter matches your data.");
        }

        List<String> headers;
        int firstDataRow;
        if (hasHeader) {
            headers = dedupeHeaders(rows.get(0));
            firstDataRow = 1;
        } else {
            headers = new ArrayList<>();
            for (int i = 0; i < widestRow(rows); i++) {
                headers.add("column" + (i + 1));
            }
            firstDataRow = 0;
        }

        ArrayNode out = json.mapper().createArrayNode();
        for (int r = firstDataRow; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            ObjectNode obj = out.addObject();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < row.size() ? row.get(c) : "";
                if (coerce) {
                    putCoerced(obj, headers.get(c), value);
                } else {
                    obj.put(headers.get(c), value);
                }
            }
        }
        return out;
    }

    private int widestRow(List<List<String>> rows) {
        int widest = 0;
        for (List<String> row : rows) {
            widest = Math.max(widest, row.size());
        }
        return widest;
    }

    /** Duplicate or blank header cells would silently overwrite each other. */
    private List<String> dedupeHeaders(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String name = raw.get(i) == null ? "" : raw.get(i).trim();
            if (name.isEmpty()) {
                name = "column" + (i + 1);
            }
            String candidate = name;
            int suffix = 2;
            while (!seen.add(candidate)) {
                candidate = name + "_" + suffix++;
            }
            out.add(candidate);
        }
        return out;
    }

    private void putCoerced(ObjectNode obj, String key, String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            obj.put(key, value == null ? "" : value);
        } else if ("true".equalsIgnoreCase(v)) {
            obj.put(key, true);
        } else if ("false".equalsIgnoreCase(v)) {
            obj.put(key, false);
        } else if ("null".equalsIgnoreCase(v)) {
            obj.putNull(key);
        } else if (NUMBER.matcher(v).matches() && !hasLeadingZero(v)) {
            obj.put(key, new BigDecimal(v));
        } else {
            obj.put(key, value);
        }
    }

    /** "007" and "0123" are identifiers in the wild, not numbers - keep them as text. */
    private boolean hasLeadingZero(String v) {
        String digits = v.startsWith("+") || v.startsWith("-") ? v.substring(1) : v;
        return digits.length() > 1 && digits.charAt(0) == '0' && digits.charAt(1) != '.';
    }

    // ==================================================================
    // JSON -> XML
    // ==================================================================

    public String jsonToXml(JsonNode root, String rootName, String itemName,
                            String indent, boolean declaration) {
        String indentToken = JsonService.indentToken(indent);
        StringBuilder sb = new StringBuilder();
        if (declaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        writeXmlNode(sb, safeElementName(rootName, "root"), root,
                safeElementName(itemName, "item"), indentToken, 0);
        return sb.toString().stripTrailing();
    }

    private void writeXmlNode(StringBuilder sb, String name, JsonNode node,
                              String itemName, String indentToken, int depth) {
        String pad = indentToken.repeat(depth);

        if (node.isNull() || node.isMissingNode()) {
            sb.append(pad).append('<').append(name).append("/>\n");
            return;
        }
        if (node.isObject()) {
            if (node.isEmpty()) {
                sb.append(pad).append('<').append(name).append("/>\n");
                return;
            }
            sb.append(pad).append('<').append(name).append(">\n");
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                writeXmlNode(sb, safeElementName(field.getKey(), "field"), field.getValue(),
                        itemName, indentToken, depth + 1);
            }
            sb.append(pad).append("</").append(name).append(">\n");
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                sb.append(pad).append('<').append(name).append("/>\n");
                return;
            }
            sb.append(pad).append('<').append(name).append(">\n");
            for (JsonNode child : node) {
                writeXmlNode(sb, itemName, child, itemName, indentToken, depth + 1);
            }
            sb.append(pad).append("</").append(name).append(">\n");
            return;
        }
        sb.append(pad).append('<').append(name).append('>')
          .append(escapeXml(node.asText()))
          .append("</").append(name).append(">\n");
    }

    /** JSON keys can be anything; XML element names cannot. */
    static String safeElementName(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean legal = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
            out.append(legal ? c : '_');
        }
        String name = out.toString();
        // A name may not start with a digit, a dot or a hyphen - prefix rather
        // than drop the character, so "2bad key" stays recognisable as _2bad_key.
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            name = "_" + name;
        }
        // "xml" in any casing is reserved by the spec.
        if (name.length() >= 3 && name.substring(0, 3).equalsIgnoreCase("xml")) {
            name = "_" + name;
        }
        return name;
    }

    static String escapeXml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&'  -> out.append("&amp;");
                case '<'  -> out.append("&lt;");
                case '>'  -> out.append("&gt;");
                case '"'  -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }

    // ==================================================================
    // XML -> JSON
    // ==================================================================

    public JsonNode xmlToJson(String xmlText, boolean keepAttributes, boolean coerce) throws Exception {
        Document doc = xml.parse(xmlText);
        Element rootElement = doc.getDocumentElement();
        ObjectNode root = json.mapper().createObjectNode();
        root.set(rootElement.getNodeName(), elementToJson(rootElement, keepAttributes, coerce));
        return root;
    }

    private JsonNode elementToJson(Element element, boolean keepAttributes, boolean coerce) {
        NamedNodeMap attributes = element.getAttributes();
        boolean hasAttributes = keepAttributes && attributes != null && attributes.getLength() > 0;

        List<Element> children = childElements(element);
        String text = directText(element);

        // Leaf element with nothing but text: collapse to a scalar.
        if (children.isEmpty() && !hasAttributes) {
            return scalar(text, coerce);
        }

        ObjectNode obj = json.mapper().createObjectNode();
        if (hasAttributes) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                obj.set("@" + attr.getName(), scalar(attr.getValue(), coerce));
            }
        }
        if (children.isEmpty()) {
            obj.set("#text", scalar(text, coerce));
            return obj;
        }

        // Group children by name so repeated siblings become an array.
        Map<String, List<Element>> grouped = new LinkedHashMap<>();
        for (Element child : children) {
            grouped.computeIfAbsent(child.getNodeName(), k -> new ArrayList<>()).add(child);
        }
        for (Map.Entry<String, List<Element>> entry : grouped.entrySet()) {
            List<Element> group = entry.getValue();
            if (group.size() == 1) {
                obj.set(entry.getKey(), elementToJson(group.get(0), keepAttributes, coerce));
            } else {
                ArrayNode array = obj.putArray(entry.getKey());
                for (Element child : group) {
                    array.add(elementToJson(child, keepAttributes, coerce));
                }
            }
        }
        if (!text.isBlank()) {
            obj.set("#text", scalar(text, coerce));
        }
        return obj;
    }

    private List<Element> childElements(Element element) {
        List<Element> out = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) child);
            }
        }
        return out;
    }

    /** Text directly inside this element, ignoring text belonging to descendants. */
    private String directText(Element element) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        return sb.toString().trim();
    }

    private JsonNode scalar(String text, boolean coerce) {
        if (!coerce) {
            return json.mapper().getNodeFactory().textNode(text);
        }
        String v = text.trim();
        if (v.isEmpty()) {
            return json.mapper().getNodeFactory().textNode(text);
        }
        if ("true".equalsIgnoreCase(v))  return json.mapper().getNodeFactory().booleanNode(true);
        if ("false".equalsIgnoreCase(v)) return json.mapper().getNodeFactory().booleanNode(false);
        if ("null".equalsIgnoreCase(v))  return json.mapper().getNodeFactory().nullNode();
        if (NUMBER.matcher(v).matches() && !hasLeadingZero(v)) {
            return json.mapper().getNodeFactory().numberNode(new BigDecimal(v));
        }
        return json.mapper().getNodeFactory().textNode(text);
    }

    // ==================================================================
    // JSON -> HTML
    // ==================================================================

    private static final String TABLE_CSS = """
            <style>
            .json-table{border-collapse:collapse;font:14px/1.5 system-ui,-apple-system,Segoe UI,sans-serif;margin:0}
            .json-table th,.json-table td{border:1px solid #d5d9e0;padding:6px 10px;text-align:left;vertical-align:top}
            .json-table th{background:#f3f5f8;font-weight:600;white-space:nowrap}
            .json-table td>table{margin:-6px -10px;border:0;width:calc(100% + 20px)}
            .json-key{font-weight:600;background:#fafbfc;white-space:nowrap}
            .json-null{color:#8a94a6;font-style:italic}
            .json-bool{color:#0b7285}
            .json-num{color:#a04000}
            </style>
            """;

    public String jsonToHtml(JsonNode root, boolean inlineCss, boolean fullPage) {
        StringBuilder body = new StringBuilder();
        writeHtmlValue(body, root);

        StringBuilder out = new StringBuilder();
        if (fullPage) {
            out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
               .append("<meta charset=\"utf-8\">\n<title>JSON Table</title>\n");
            if (inlineCss) {
                out.append(TABLE_CSS);
            }
            out.append("</head>\n<body>\n").append(body).append("\n</body>\n</html>");
        } else {
            if (inlineCss) {
                out.append(TABLE_CSS);
            }
            out.append(body);
        }
        return out.toString();
    }

    private void writeHtmlValue(StringBuilder sb, JsonNode node) {
        if (node.isObject()) {
            writeHtmlObject(sb, node);
        } else if (node.isArray()) {
            writeHtmlArray(sb, node);
        } else {
            sb.append(htmlScalar(node));
        }
    }

    private void writeHtmlObject(StringBuilder sb, JsonNode node) {
        if (node.isEmpty()) {
            sb.append("<table class=\"json-table\"><tr><td class=\"json-null\">empty object</td></tr></table>");
            return;
        }
        sb.append("<table class=\"json-table\">");
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            sb.append("<tr><td class=\"json-key\">").append(escapeHtml(field.getKey())).append("</td><td>");
            writeHtmlValue(sb, field.getValue());
            sb.append("</td></tr>");
        }
        sb.append("</table>");
    }

    private void writeHtmlArray(StringBuilder sb, JsonNode node) {
        if (node.isEmpty()) {
            sb.append("<table class=\"json-table\"><tr><td class=\"json-null\">empty array</td></tr></table>");
            return;
        }
        // An array of objects reads best as a real table with one column per key.
        if (isObjectArray(node)) {
            LinkedHashSet<String> columns = new LinkedHashSet<>();
            for (JsonNode row : node) {
                row.fieldNames().forEachRemaining(columns::add);
            }
            sb.append("<table class=\"json-table\"><thead><tr>");
            for (String column : columns) {
                sb.append("<th>").append(escapeHtml(column)).append("</th>");
            }
            sb.append("</tr></thead><tbody>");
            for (JsonNode row : node) {
                sb.append("<tr>");
                for (String column : columns) {
                    sb.append("<td>");
                    JsonNode value = row.get(column);
                    if (value == null) {
                        sb.append("<span class=\"json-null\">-</span>");
                    } else {
                        writeHtmlValue(sb, value);
                    }
                    sb.append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
            return;
        }
        sb.append("<table class=\"json-table\">");
        for (int i = 0; i < node.size(); i++) {
            sb.append("<tr><td class=\"json-key\">").append(i).append("</td><td>");
            writeHtmlValue(sb, node.get(i));
            sb.append("</td></tr>");
        }
        sb.append("</table>");
    }

    private boolean isObjectArray(JsonNode array) {
        for (JsonNode child : array) {
            if (!child.isObject()) {
                return false;
            }
        }
        return true;
    }

    private String htmlScalar(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) {
            return "<span class=\"json-null\">null</span>";
        }
        if (node.isBoolean()) {
            return "<span class=\"json-bool\">" + node.asText() + "</span>";
        }
        if (node.isNumber()) {
            return "<span class=\"json-num\">" + node.asText() + "</span>";
        }
        return escapeHtml(node.asText());
    }

    static String escapeHtml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default  -> out.append(c);
            }
        }
        return out.toString();
    }

    // ==================================================================
    // JSON -> SQL
    // ==================================================================

    public String jsonToSql(JsonNode root, String tableName, String dialect,
                            boolean multiRow, boolean createTable) {
        Table table = flatten.toTable(root, true);
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Nothing to insert - the document produced no rows.");
        }
        String quotedTable = quoteIdentifier(tableName == null || tableName.isBlank() ? "my_table" : tableName.trim(), dialect);

        StringBuilder sb = new StringBuilder();
        if (createTable) {
            sb.append(createTableStatement(quotedTable, table, dialect)).append("\n\n");
        }

        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < table.headers().size(); i++) {
            if (i > 0) {
                columns.append(", ");
            }
            columns.append(quoteIdentifier(table.headers().get(i), dialect));
        }

        if (multiRow) {
            sb.append("INSERT INTO ").append(quotedTable).append(" (").append(columns).append(")\nVALUES\n");
            for (int r = 0; r < table.rows().size(); r++) {
                sb.append("  (").append(valueList(table.rows().get(r))).append(')')
                  .append(r == table.rows().size() - 1 ? ";" : ",").append('\n');
            }
        } else {
            for (List<Cell> row : table.rows()) {
                sb.append("INSERT INTO ").append(quotedTable).append(" (").append(columns)
                  .append(") VALUES (").append(valueList(row)).append(");\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private String createTableStatement(String quotedTable, Table table, String dialect) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(quotedTable).append(" (\n");
        for (int c = 0; c < table.headers().size(); c++) {
            sb.append("  ").append(quoteIdentifier(table.headers().get(c), dialect))
              .append(' ').append(sqlTypeOf(table, c));
            sb.append(c == table.headers().size() - 1 ? "\n" : ",\n");
        }
        return sb.append(");").toString();
    }

    /** Widest type seen down the column: numbers stay numeric only if every row is numeric. */
    private String sqlTypeOf(Table table, int column) {
        boolean allNumeric = true;
        boolean allBoolean = true;
        boolean anyValue = false;
        int widest = 1;
        for (List<Cell> row : table.rows()) {
            Cell cell = row.get(column);
            if (cell.type() == CellType.NULL) {
                continue;
            }
            anyValue = true;
            widest = Math.max(widest, cell.text().length());
            allNumeric &= cell.type() == CellType.NUMBER;
            allBoolean &= cell.type() == CellType.BOOLEAN;
        }
        if (!anyValue) {
            return "VARCHAR(255)";
        }
        if (allBoolean) {
            return "BOOLEAN";
        }
        if (allNumeric) {
            return "NUMERIC";
        }
        int size = Math.min(4000, Math.max(255, ((widest / 255) + 1) * 255));
        return "VARCHAR(" + size + ")";
    }

    private String valueList(List<Cell> row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sqlLiteral(row.get(i)));
        }
        return sb.toString();
    }

    static String sqlLiteral(Cell cell) {
        return switch (cell.type()) {
            case NULL -> "NULL";
            case NUMBER -> cell.text();
            case BOOLEAN -> cell.text().toUpperCase();
            case STRING -> "'" + cell.text().replace("'", "''") + "'";
        };
    }

    static String quoteIdentifier(String name, String dialect) {
        String cleaned = name.replace(" ", "");
        return switch (dialect == null ? "ansi" : dialect) {
            case "mysql" -> "`" + cleaned.replace("`", "``") + "`";
            case "mssql" -> "[" + cleaned.replace("]", "]]") + "]";
            case "none"  -> cleaned;
            default      -> "\"" + cleaned.replace("\"", "\"\"") + "\"";
        };
    }
}
