package com.jsontools.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.jsontools.model.ToolOptions;
import com.jsontools.model.ToolResult;
import com.jsontools.model.TypeNode;
import com.jsontools.service.DiffService.Difference;
import com.jsontools.service.FlattenService.Table;
import com.jsontools.service.SchemaService.Violation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs a tool by id. Every branch returns a {@link ToolResult}; failures are
 * turned into a result carrying the message (and error position where the
 * parser gave us one) rather than propagating.
 */
@Service
public class ToolExecutor {

    private final JsonService json;
    private final XmlService xml;
    private final YamlService yaml;
    private final CsvService csv;
    private final ConversionService conversion;
    private final FlattenService flatten;
    private final CodeGenService codeGen;
    private final TypeInferrer inferrer;
    private final SchemaService schema;
    private final DiffService diff;
    private final EscapeService escape;
    private final ExcelService excel;

    public ToolExecutor(JsonService json, XmlService xml, YamlService yaml, CsvService csv,
                        ConversionService conversion, FlattenService flatten, CodeGenService codeGen,
                        TypeInferrer inferrer, SchemaService schema, DiffService diff,
                        EscapeService escape, ExcelService excel) {
        this.json = json;
        this.xml = xml;
        this.yaml = yaml;
        this.csv = csv;
        this.conversion = conversion;
        this.flatten = flatten;
        this.codeGen = codeGen;
        this.inferrer = inferrer;
        this.schema = schema;
        this.diff = diff;
        this.escape = escape;
        this.excel = excel;
    }

    public ToolResult execute(String toolId, String input, String secondInput, ToolOptions options) {
        try {
            return run(toolId, input == null ? "" : input, secondInput == null ? "" : secondInput, options);
        } catch (JsonProcessingException e) {
            int line = e.getLocation() != null ? e.getLocation().getLineNr() : 0;
            int column = e.getLocation() != null ? e.getLocation().getColumnNr() : 0;
            return ToolResult.error(e.getOriginalMessage(), line, column);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            String message = e.getMessage();
            return ToolResult.error(message == null || message.isBlank() ? e.toString() : message);
        }
    }

    private ToolResult run(String id, String input, String second, ToolOptions o) throws Exception {
        return switch (id) {
            // ----------------------------------------------------------
            // Format & beautify
            // ----------------------------------------------------------
            case "json-formatter", "json-editor" -> {
                JsonNode node = json.read(input);
                if (o.flag("sortKeys", false)) {
                    node = json.sortNode(node, false, true);
                }
                String output = json.write(node, o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "json-viewer" -> {
                JsonNode node = json.read(input);
                String output = json.write(node, o.indent());
                yield ToolResult.ok(output, treeStats(node));
            }
            case "json-minify" -> {
                String output = json.minify(input);
                yield ToolResult.ok(output, savingStats(input, output));
            }
            case "json-sorter" -> {
                JsonNode node = json.sortNode(json.read(input),
                        "desc".equalsIgnoreCase(o.get("direction", "asc")),
                        o.flag("recursive", true));
                String output = json.write(node, o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "json-fixer" -> {
                // The lenient parser accepts the broken forms; the strict writer emits valid JSON.
                String output = json.write(json.read(input), o.indent());
                yield ToolResult.ok(output, Map.of("Result", "Repaired into valid JSON"));
            }
            case "xml-formatter" -> {
                String output = xml.format(input, o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "xml-minify" -> {
                String output = xml.minify(input);
                yield ToolResult.ok(output, savingStats(input, output));
            }
            case "yaml-formatter" -> {
                String output = yaml.format(input);
                yield ToolResult.ok(output, sizeStats(input, output));
            }

            // ----------------------------------------------------------
            // Validate & query
            // ----------------------------------------------------------
            case "json-validator" -> {
                JsonService.Validation v = json.validate(input);
                if (!v.isValid()) {
                    yield ToolResult.error(v.getMessage(), v.getLine(), v.getColumn());
                }
                Map<String, String> stats = new LinkedHashMap<>();
                stats.put("Root", v.getRootType());
                stats.put("Nodes", String.valueOf(v.getNodeCount()));
                stats.put("Size", bytes(input));
                yield ToolResult.ok("Valid JSON", stats);
            }
            case "xml-validator" -> {
                XmlService.Validation v = xml.validate(input);
                yield v.isValid()
                        ? ToolResult.ok("Valid XML", Map.of("Size", bytes(input)))
                        : ToolResult.error(v.getMessage(), v.getLine(), v.getColumn());
            }
            case "yaml-validator" -> {
                YamlService.Validation v = yaml.validate(input);
                yield v.isValid()
                        ? ToolResult.ok("Valid YAML", Map.of("Size", bytes(input)))
                        : ToolResult.error(v.getMessage());
            }
            case "json-schema-validator" -> {
                JsonNode data = json.read(input);
                JsonNode schemaNode = readSecond(second, "Paste the JSON Schema into the second pane.");
                List<Violation> violations = schema.validate(data, schemaNode, o.get("draft", "auto"));
                if (violations.isEmpty()) {
                    yield ToolResult.ok("The document is valid against the schema.",
                            Map.of("Violations", "0"));
                }
                List<Map<String, String>> rows = new ArrayList<>(violations.size());
                StringBuilder text = new StringBuilder();
                for (Violation violation : violations) {
                    rows.add(Map.of("location", violation.location(), "message", violation.message()));
                    text.append(violation.location()).append(": ").append(violation.message()).append('\n');
                }
                yield new ToolResult(false, text.toString().stripTrailing(),
                        violations.size() + " schema violation" + (violations.size() == 1 ? "" : "s") + " found.",
                        0, 0, Map.of("Violations", String.valueOf(violations.size())), rows);
            }
            case "json-schema-generator" -> {
                TypeNode type = inferrer.infer(json.read(input), "Root");
                JsonNode generated = schema.generate(type, o.get("draft", "2020-12"), o.flag("required", true));
                yield ToolResult.ok(json.write(generated, o.indent()));
            }
            case "jsonpath-tester" -> {
                String output = json.query(input, o.get("path", "$"), o.indent());
                yield ToolResult.ok(output, Map.of("Expression", o.get("path", "$")));
            }
            case "json-diff" -> {
                JsonNode left = json.read(input);
                JsonNode right = readSecond(second, "Paste the second document into the right pane.");
                List<Difference> differences = diff.diff(left, right,
                        new DiffService.Options(o.flag("ignoreArrayOrder", false), o.flag("ignoreCase", false)));

                List<Map<String, String>> rows = new ArrayList<>(differences.size());
                StringBuilder text = new StringBuilder();
                for (Difference d : differences) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("path", d.path());
                    row.put("type", d.type().getLabel());
                    row.put("typeClass", d.type().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    row.put("left", d.left());
                    row.put("right", d.right());
                    rows.add(row);
                    text.append(d.type().getLabel()).append("  ").append(d.path())
                        .append("\n    left : ").append(d.left())
                        .append("\n    right: ").append(d.right()).append('\n');
                }
                Map<String, String> stats = Map.of("Differences", String.valueOf(differences.size()));
                yield ToolResult.ok(
                        differences.isEmpty() ? diff.summarise(differences) : text.toString().stripTrailing(),
                        stats, rows);
            }
            case "json-merge" -> {
                JsonNode base = json.read(input);
                JsonNode patch = readSecond(second, "Paste the document to merge in on the right.");
                JsonNode merged = diff.merge(base, patch,
                        new DiffService.MergeOptions("concat".equals(o.get("arrays", "replace")),
                                o.flag("nullRemoves", false)));
                yield ToolResult.ok(json.write(merged, o.indent()));
            }

            // ----------------------------------------------------------
            // Convert
            // ----------------------------------------------------------
            case "json-to-xml" -> {
                String output = conversion.jsonToXml(json.read(input),
                        o.get("rootName", "root"), o.get("itemName", "item"),
                        o.indent(), o.flag("declaration", true));
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "xml-to-json" -> {
                JsonNode node = conversion.xmlToJson(input, o.flag("attributes", true), o.flag("coerce", false));
                String output = json.write(node, o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "json-to-yaml" -> {
                String output = yaml.write(json.read(input));
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "yaml-to-json" -> {
                String output = json.write(yaml.read(input), o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "json-to-csv", "json-to-tsv" -> {
                char delimiter = "json-to-tsv".equals(id)
                        ? '\t'
                        : CsvService.delimiterOf(o.get("delimiter", "comma"));
                JsonNode node = json.read(input);
                String output = conversion.jsonToDelimited(node, delimiter,
                        o.flag("header", true), o.flag("flatten", true));
                yield ToolResult.ok(output, tableStats(flatten.toTable(node, o.flag("flatten", true))));
            }
            case "csv-to-json", "tsv-to-json" -> {
                char delimiter = "tsv-to-json".equals(id)
                        ? '\t'
                        : CsvService.delimiterOf(o.get("delimiter", "comma"));
                JsonNode node = conversion.delimitedToJson(input, delimiter,
                        o.flag("header", true), o.flag("coerce", true));
                String output = json.write(node, o.indent());
                yield ToolResult.ok(output, Map.of("Records", String.valueOf(node.size())));
            }
            case "json-to-html" -> {
                String output = conversion.jsonToHtml(json.read(input),
                        o.flag("inlineCss", true), o.flag("fullPage", false));
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "json-to-excel" -> {
                // The pane shows a preview; the actual workbook is built on download.
                Table table = flatten.toTable(json.read(input), o.flag("flatten", true));
                String preview = conversion.jsonToDelimited(json.read(input), '\t', true, o.flag("flatten", true));
                yield ToolResult.ok(preview, tableStats(table));
            }
            case "json-to-sql" -> {
                String output = conversion.jsonToSql(json.read(input),
                        o.get("tableName", "my_table"), o.get("dialect", "ansi"),
                        o.flag("multiRow", false), o.flag("createTable", false));
                yield ToolResult.ok(output, tableStats(flatten.toTable(json.read(input), true)));
            }
            case "json-to-string" -> {
                String output = json.stringify(input);
                yield ToolResult.ok(output, sizeStats(input, output));
            }
            case "string-to-json" -> {
                String output = json.parseString(input, o.indent());
                yield ToolResult.ok(output, sizeStats(input, output));
            }

            // ----------------------------------------------------------
            // Code generation
            // ----------------------------------------------------------
            case "json-to-java" -> ToolResult.ok(codeGen.toJava(
                    inferType(input, o), o.get("className", "Root"), o.get("packageName", ""),
                    "record".equals(o.get("style", "class")), o.flag("jsonProperty", false)));
            case "json-to-typescript" -> ToolResult.ok(codeGen.toTypeScript(
                    inferType(input, o), o.get("className", "Root"),
                    "type".equals(o.get("style", "interface"))));
            case "json-to-csharp" -> ToolResult.ok(codeGen.toCSharp(
                    inferType(input, o), o.get("className", "Root"), o.get("packageName", ""),
                    o.flag("jsonProperty", true)));
            case "json-to-python" -> ToolResult.ok(codeGen.toPython(
                    inferType(input, o), o.get("className", "Root")));
            case "json-to-go" -> ToolResult.ok(codeGen.toGo(
                    inferType(input, o), o.get("className", "Root"), o.get("packageName", "main")));

            // ----------------------------------------------------------
            // Text utilities
            // ----------------------------------------------------------
            case "json-escape" -> ToolResult.ok(escape.escape(requireText(input),
                    o.flag("quotes", false), o.flag("escapeNonAscii", false)));
            case "json-unescape" -> ToolResult.ok(escape.unescape(requireText(input)));
            case "base64-encode" -> ToolResult.ok(escape.base64Encode(requireText(input),
                    o.flag("urlSafe", false), o.flag("padding", true)));
            case "base64-decode" -> ToolResult.ok(escape.base64Decode(requireText(input)));
            case "url-encode" -> ToolResult.ok(escape.urlEncode(requireText(input),
                    "query".equals(o.get("mode", "query"))));
            case "url-decode" -> ToolResult.ok(escape.urlDecode(requireText(input),
                    "query".equals(o.get("mode", "query"))));

            default -> ToolResult.error("Unknown tool: " + id);
        };
    }

    /** Builds the workbook for the JSON to Excel download. */
    public byte[] excelWorkbook(String input, ToolOptions options) throws Exception {
        Table table = flatten.toTable(json.read(input), options.flag("flatten", true));
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Nothing to export - the document produced no rows.");
        }
        return excel.toWorkbook(table, options.get("sheetName", "Sheet1"));
    }

    private TypeNode inferType(String input, ToolOptions options) throws Exception {
        return inferrer.infer(json.read(input), options.get("className", "Root"));
    }

    private JsonNode readSecond(String second, String message) throws Exception {
        if (second == null || second.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return json.read(second);
    }

    private String requireText(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("No input provided.");
        }
        return input;
    }

    // ------------------------------------------------------------------
    // Statistics shown under the output pane
    // ------------------------------------------------------------------

    private Map<String, String> sizeStats(String input, String output) {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Input", bytes(input));
        stats.put("Output", bytes(output));
        return stats;
    }

    private Map<String, String> savingStats(String input, String output) {
        int before = byteLength(input);
        int after = byteLength(output);
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Input", bytes(input));
        stats.put("Output", bytes(output));
        if (before > 0 && after <= before) {
            stats.put("Saved", String.format(Locale.ROOT, "%.1f%%", 100.0 * (before - after) / before));
        }
        return stats;
    }

    private Map<String, String> treeStats(JsonNode node) {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Nodes", String.valueOf(json.count(node)));
        stats.put("Root", node.isArray() ? "Array (" + node.size() + " items)"
                : node.isObject() ? "Object (" + node.size() + " keys)"
                : node.getNodeType().toString().toLowerCase(Locale.ROOT));
        return stats;
    }

    private Map<String, String> tableStats(Table table) {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Rows", String.valueOf(table.rows().size()));
        stats.put("Columns", String.valueOf(table.headers().size()));
        return stats;
    }

    private static int byteLength(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String bytes(String text) {
        int length = byteLength(text);
        if (length < 1024) {
            return length + " B";
        }
        if (length < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", length / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", length / (1024.0 * 1024.0));
    }
}
