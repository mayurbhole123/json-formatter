package com.jsontools.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Core JSON operations: parse, beautify, minify, validate, sort, stringify
 * and JSONPath query. Everything else in the app funnels through here.
 */
@Service
public class JsonService {

    /** Strict parser - used by the validator so bad JSON is actually reported. */
    private final ObjectMapper strict = buildMapper(false);

    /** Forgiving parser - lets the formatter repair single quotes, comments, trailing commas. */
    private final ObjectMapper lenient = buildMapper(true);

    private static ObjectMapper buildMapper(boolean relaxed) {
        ObjectMapper m = new ObjectMapper();
        // Keep 1.20 as 1.20 and huge integers intact instead of collapsing to double.
        m.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        m.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        m.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        if (relaxed) {
            m.enable(JsonParser.Feature.ALLOW_COMMENTS);
            m.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            m.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
            m.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            m.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
        }
        return m;
    }

    public ObjectMapper mapper() {
        return strict;
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Parses leniently - what the Beautify / convert tools use. */
    public JsonNode read(String json) throws IOException {
        requireText(json);
        return lenient.readTree(json);
    }

    /** Parses strictly - what the Validator uses. */
    public JsonNode readStrict(String json) throws IOException {
        requireText(json);
        return strict.readTree(json);
    }

    private void requireText(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("No input provided. Paste or upload some JSON first.");
        }
    }

    // ------------------------------------------------------------------
    // Beautify / minify
    // ------------------------------------------------------------------

    public String format(String json, String indent) throws IOException {
        return write(read(json), indent);
    }

    public String write(JsonNode node, String indent) throws IOException {
        return strict.writer(prettyPrinter(indentToken(indent))).writeValueAsString(node);
    }

    public String minify(String json) throws IOException {
        return strict.writer().writeValueAsString(read(json));
    }

    /**
     * Translates the UI indent choice into the literal string used per level.
     * Accepts "2", "3", "4", "tab" or an explicit run of whitespace.
     */
    public static String indentToken(String indent) {
        if (indent == null || indent.trim().isEmpty()) {
            return "  ";
        }
        String v = indent.trim();
        if ("tab".equalsIgnoreCase(v) || "\t".equals(v)) {
            return "\t";
        }
        try {
            int n = Integer.parseInt(v);
            if (n < 0) n = 0;
            if (n > 12) n = 12;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(' ');
            return sb.toString();
        } catch (NumberFormatException ignored) {
            return "  ";
        }
    }

    private static PrettyPrinter prettyPrinter(String indentToken) {
        JsonToolsPrettyPrinter pp = new JsonToolsPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter(indentToken, "\n");
        pp.indentObjectsWith(indenter);
        pp.indentArraysWith(indenter);
        return pp;
    }

    /**
     * Jackson's stock printer emits {@code "key" : value} and {@code [ ]}.
     * This produces the conventional {@code "key": value} and {@code []}.
     */
    private static class JsonToolsPrettyPrinter extends DefaultPrettyPrinter {
        private static final long serialVersionUID = 1L;

        JsonToolsPrettyPrinter() {
            super();
        }

        JsonToolsPrettyPrinter(JsonToolsPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JsonToolsPrettyPrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
            g.writeRaw(": ");
        }

        @Override
        public void writeEndObject(JsonGenerator g, int nrOfEntries) throws IOException {
            if (nrOfEntries == 0) {
                g.writeRaw('}');
            } else {
                super.writeEndObject(g, nrOfEntries);
            }
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
            if (nrOfValues == 0) {
                g.writeRaw(']');
            } else {
                super.writeEndArray(g, nrOfValues);
            }
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    public static class Validation {
        private final boolean valid;
        private final String message;
        private final int line;
        private final int column;
        private final String rootType;
        private final int nodeCount;

        Validation(boolean valid, String message, int line, int column, String rootType, int nodeCount) {
            this.valid = valid;
            this.message = message;
            this.line = line;
            this.column = column;
            this.rootType = rootType;
            this.nodeCount = nodeCount;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getRootType() { return rootType; }
        public int getNodeCount() { return nodeCount; }
    }

    public Validation validate(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new Validation(false, "No input provided.", 0, 0, null, 0);
        }
        try {
            JsonNode node = strict.readTree(json);
            if (node == null || node.isMissingNode()) {
                return new Validation(false, "Input is empty or not valid JSON.", 0, 0, null, 0);
            }
            return new Validation(true, "Valid JSON", 0, 0, describe(node), count(node));
        } catch (JsonProcessingException e) {
            int line = e.getLocation() != null ? e.getLocation().getLineNr() : 0;
            int col = e.getLocation() != null ? e.getLocation().getColumnNr() : 0;
            return new Validation(false, e.getOriginalMessage(), line, col, null, 0);
        }
    }

    private String describe(JsonNode n) {
        if (n.isArray()) return "Array (" + n.size() + " items)";
        if (n.isObject()) return "Object (" + n.size() + " keys)";
        return n.getNodeType().toString().toLowerCase();
    }

    /** Total number of nodes in the tree - shown as a statistic after validating. */
    public int count(JsonNode n) {
        int total = 1;
        for (JsonNode child : n) {
            total += count(child);
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Sort
    // ------------------------------------------------------------------

    public String sort(String json, boolean descending, boolean recursive, String indent) throws IOException {
        JsonNode sorted = sortNode(read(json), descending, recursive);
        return write(sorted, indent);
    }

    /** Sorts the keys of an already-parsed document. */
    public JsonNode sortNode(JsonNode node, boolean descending, boolean recursive) {
        return sortNode(node, descending, recursive, true);
    }

    private JsonNode sortNode(JsonNode node, boolean desc, boolean recursive, boolean topLevel) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) names.add(it.next());

            Comparator<String> cmp = String.CASE_INSENSITIVE_ORDER;
            names.sort(desc ? cmp.reversed() : cmp);

            ObjectNode out = strict.createObjectNode();
            for (String name : names) {
                JsonNode child = node.get(name);
                out.set(name, (recursive || topLevel) ? sortNode(child, desc, recursive, false) : child);
            }
            return out;
        }
        if (node.isArray() && recursive) {
            ArrayNode out = strict.createArrayNode();
            for (JsonNode child : node) {
                out.add(sortNode(child, desc, recursive, false));
            }
            return out;
        }
        return node;
    }

    // ------------------------------------------------------------------
    // Stringify / parse-string
    // ------------------------------------------------------------------

    /** JSON value -> a quoted JSON string literal containing that JSON. */
    public String stringify(String json) throws IOException {
        String compact = minify(json);
        return strict.writeValueAsString(compact);
    }

    /** A quoted JSON string literal (or raw escaped text) -> the JSON it holds. */
    public String parseString(String input, String indent) throws IOException {
        requireText(input);
        String trimmed = input.trim();
        String inner;
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            inner = strict.readValue(trimmed, String.class);
        } else {
            // Not quoted - treat the text itself as an escaped payload.
            inner = strict.readValue("\"" + trimmed.replace("\"", "\\\"") + "\"", String.class);
        }
        return format(inner, indent);
    }

    // ------------------------------------------------------------------
    // JSONPath
    // ------------------------------------------------------------------

    private final Configuration jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider(strict))
            .mappingProvider(new JacksonMappingProvider(strict))
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    public String query(String json, String path, String indent) throws IOException {
        requireText(json);
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a JSONPath expression, for example $..name or $.store.book[0]");
        }
        Object result = JsonPath.using(jsonPathConfig).parse(json).read(path.trim());
        if (result == null) {
            return "null";
        }
        JsonNode node = (result instanceof JsonNode)
                ? (JsonNode) result
                : strict.valueToTree(result);
        return write(node, indent);
    }
}
