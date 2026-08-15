package com.jsontools.registry;

import com.jsontools.model.Tool;
import com.jsontools.model.Tool.Category;
import com.jsontools.model.Tool.Editor;
import com.jsontools.model.ToolOption;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogue of every tool the site offers. The navigation, the home page
 * and the generic tool page are all rendered from this, so a new tool means one
 * entry here plus one branch in {@code ToolExecutor}.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    @PostConstruct
    void register() {
        // ------------------------------------------------------------------
        // Format & beautify
        // ------------------------------------------------------------------
        add(Tool.of("json-formatter", "JSON Formatter")
                .tagline("Beautify JSON with the indentation you want")
                .description("Pretty-prints JSON with 2/3/4-space or tab indentation. "
                        + "The parser is forgiving, so comments, single quotes and trailing commas are accepted and cleaned up.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("JSON Input").outputLabel("Formatted JSON")
                .action("Format / Beautify")
                .download("formatted.json")
                .options(ToolOption.indent(),
                        ToolOption.toggle("sortKeys", "Sort keys", false)));

        add(Tool.of("json-viewer", "JSON Viewer")
                .tagline("Browse JSON as a collapsible tree")
                .description("Renders JSON as an expandable tree so you can walk large documents, "
                        + "collapse noisy branches and copy the JSONPath of any node.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.TREE)
                .inputLabel("JSON Input").outputLabel("Tree View")
                .action("View")
                .options(ToolOption.indent()));

        add(Tool.of("json-editor", "JSON Editor")
                .tagline("Edit, re-format and validate in place")
                .description("A working pane that formats as you go and reports errors inline. "
                        + "Edit the output, push it back to the input and keep going.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("JSON Document").outputLabel("Result")
                .action("Apply")
                .download("document.json")
                .options(ToolOption.indent(),
                        ToolOption.toggle("sortKeys", "Sort keys", false)));

        add(Tool.of("json-minify", "JSON Minify")
                .tagline("Strip every byte that is not needed")
                .description("Removes whitespace, newlines and comments to produce the smallest valid JSON, "
                        + "and reports how much was saved.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("JSON Input").outputLabel("Minified JSON")
                .action("Minify / Compact")
                .download("minified.json"));

        add(Tool.of("json-sorter", "JSON Sorter")
                .tagline("Order object keys alphabetically")
                .description("Sorts object keys ascending or descending, optionally through the whole tree, "
                        + "which makes two documents comparable line by line.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("JSON Input").outputLabel("Sorted JSON")
                .action("Sort")
                .download("sorted.json")
                .options(ToolOption.indent(),
                        ToolOption.select("direction", "Order", "asc", "asc", "A to Z", "desc", "Z to A"),
                        ToolOption.toggle("recursive", "Sort nested objects", true)));

        add(Tool.of("json-fixer", "JSON Fixer")
                .tagline("Repair almost-JSON into valid JSON")
                .description("Accepts the usual hand-written mistakes - single quotes, unquoted keys, "
                        + "trailing commas, // and # comments - and emits strict RFC 8259 JSON.")
                .category(Category.FORMAT)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("Broken JSON").outputLabel("Repaired JSON")
                .action("Fix JSON")
                .download("fixed.json")
                .options(ToolOption.indent()));

        add(Tool.of("xml-formatter", "XML Formatter")
                .tagline("Beautify and indent XML")
                .description("Re-indents XML documents. External entity resolution is disabled, "
                        + "so pasting untrusted XML is safe.")
                .category(Category.FORMAT)
                .in(Editor.XML).out(Editor.XML)
                .inputLabel("XML Input").outputLabel("Formatted XML")
                .action("Format XML")
                .download("formatted.xml")
                .options(ToolOption.indent()));

        add(Tool.of("xml-minify", "XML Minify")
                .tagline("Compact XML onto a single line")
                .description("Drops insignificant whitespace between elements to shrink an XML payload.")
                .category(Category.FORMAT)
                .in(Editor.XML).out(Editor.XML)
                .inputLabel("XML Input").outputLabel("Minified XML")
                .action("Minify XML")
                .download("minified.xml"));

        add(Tool.of("yaml-formatter", "YAML Formatter")
                .tagline("Normalise YAML indentation and quoting")
                .description("Round-trips YAML through a parser, which produces consistent indentation, "
                        + "quoting and block style.")
                .category(Category.FORMAT)
                .in(Editor.YAML).out(Editor.YAML)
                .inputLabel("YAML Input").outputLabel("Formatted YAML")
                .action("Format YAML")
                .download("formatted.yaml"));

        // ------------------------------------------------------------------
        // Validate & query
        // ------------------------------------------------------------------
        add(Tool.of("json-validator", "JSON Validator")
                .tagline("Check syntax and get the exact error position")
                .description("Validates against RFC 8259 and reports the line and column of the first problem, "
                        + "plus statistics about the document.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.REPORT)
                .inputLabel("JSON Input").outputLabel("Validation Result")
                .action("Validate JSON"));

        add(Tool.of("xml-validator", "XML Validator")
                .tagline("Check that XML is well formed")
                .description("Parses the document and reports the line and column of the first syntax error.")
                .category(Category.VALIDATE)
                .in(Editor.XML).out(Editor.REPORT)
                .inputLabel("XML Input").outputLabel("Validation Result")
                .action("Validate XML"));

        add(Tool.of("yaml-validator", "YAML Validator")
                .tagline("Check YAML syntax")
                .description("Parses YAML and reports the first error, including the mark where it occurred.")
                .category(Category.VALIDATE)
                .in(Editor.YAML).out(Editor.REPORT)
                .inputLabel("YAML Input").outputLabel("Validation Result")
                .action("Validate YAML"));

        add(Tool.of("json-schema-validator", "JSON Schema Validator")
                .tagline("Validate a document against a JSON Schema")
                .description("Supports drafts 4, 6, 7, 2019-09 and 2020-12. Every violation is listed with "
                        + "the instance location that failed.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.REPORT)
                .inputLabel("JSON Document").secondInput("JSON Schema").outputLabel("Validation Result")
                .action("Validate Against Schema")
                .options(ToolOption.select("draft", "Draft", "auto",
                        "auto", "Detect from $schema",
                        "V202012", "2020-12",
                        "V201909", "2019-09",
                        "V7", "Draft 7",
                        "V6", "Draft 6",
                        "V4", "Draft 4")));

        add(Tool.of("json-schema-generator", "JSON Schema Generator")
                .tagline("Infer a schema from a sample document")
                .description("Walks a sample document and produces a JSON Schema describing it, "
                        + "merging the shape of every array element.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("Sample JSON").outputLabel("JSON Schema")
                .action("Generate Schema")
                .download("schema.json")
                .options(ToolOption.indent(),
                        ToolOption.select("draft", "Draft", "2020-12",
                                "2020-12", "2020-12",
                                "07", "Draft 7"),
                        ToolOption.toggle("required", "Mark present keys as required", true)));

        add(Tool.of("jsonpath-tester", "JSONPath Tester")
                .tagline("Run a JSONPath expression against a document")
                .description("Evaluates expressions such as $..book[?(@.price<10)] and shows the matches. "
                        + "Useful for building selectors before wiring them into code.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("JSON Input").outputLabel("Matches")
                .action("Evaluate")
                .options(ToolOption.text("path", "JSONPath", "$", "$..name"),
                        ToolOption.indent()));

        add(Tool.of("json-diff", "JSON Diff")
                .tagline("Compare two documents key by key")
                .description("Structural comparison that ignores key order and formatting, listing every "
                        + "added, removed and changed value with its path.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.DIFF)
                .inputLabel("Left JSON").secondInput("Right JSON").outputLabel("Differences")
                .action("Compare")
                .options(ToolOption.toggle("ignoreArrayOrder", "Ignore array order", false),
                        ToolOption.toggle("ignoreCase", "Ignore string case", false)));

        add(Tool.of("json-merge", "JSON Merge")
                .tagline("Deep-merge two documents")
                .description("Merges the right document into the left one. Objects combine recursively; "
                        + "arrays either replace or concatenate.")
                .category(Category.VALIDATE)
                .in(Editor.JSON).out(Editor.JSON)
                .inputLabel("Base JSON").secondInput("JSON to merge in").outputLabel("Merged JSON")
                .action("Merge")
                .download("merged.json")
                .options(ToolOption.indent(),
                        ToolOption.select("arrays", "Arrays", "replace",
                                "replace", "Replace", "concat", "Concatenate"),
                        ToolOption.toggle("nullRemoves", "null in the patch deletes the key", false)));

        // ------------------------------------------------------------------
        // Convert
        // ------------------------------------------------------------------
        add(Tool.of("json-to-xml", "JSON to XML")
                .tagline("Convert JSON into an XML document")
                .description("Objects become elements, arrays repeat their element, and names that are not "
                        + "valid XML are sanitised.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.XML)
                .inputLabel("JSON Input").outputLabel("XML Output")
                .action("Convert to XML")
                .download("converted.xml")
                .options(ToolOption.indent(),
                        ToolOption.text("rootName", "Root element", "root", "root"),
                        ToolOption.text("itemName", "Array item element", "item", "item"),
                        ToolOption.toggle("declaration", "XML declaration", true)));

        add(Tool.of("xml-to-json", "XML to JSON")
                .tagline("Convert an XML document into JSON")
                .description("Elements become object keys, repeated siblings become arrays, and attributes "
                        + "are kept under an @-prefixed key.")
                .category(Category.CONVERT)
                .in(Editor.XML).out(Editor.JSON)
                .inputLabel("XML Input").outputLabel("JSON Output")
                .action("Convert to JSON")
                .download("converted.json")
                .options(ToolOption.indent(),
                        ToolOption.toggle("attributes", "Keep attributes", true),
                        ToolOption.toggle("coerce", "Detect numbers and booleans", false)));

        add(Tool.of("json-to-yaml", "JSON to YAML")
                .tagline("Convert JSON into YAML")
                .description("Emits block-style YAML with minimal quoting.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.YAML)
                .inputLabel("JSON Input").outputLabel("YAML Output")
                .action("Convert to YAML")
                .download("converted.yaml"));

        add(Tool.of("yaml-to-json", "YAML to JSON")
                .tagline("Convert YAML into JSON")
                .description("Parses YAML (including anchors and multi-line scalars) and prints it as JSON.")
                .category(Category.CONVERT)
                .in(Editor.YAML).out(Editor.JSON)
                .inputLabel("YAML Input").outputLabel("JSON Output")
                .action("Convert to JSON")
                .download("converted.json")
                .options(ToolOption.indent()));

        add(Tool.of("json-to-csv", "JSON to CSV")
                .tagline("Flatten JSON into a spreadsheet")
                .description("Turns an array of objects into rows. Nested objects are flattened to dotted "
                        + "column names and the header is the union of every key seen.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.CSV)
                .inputLabel("JSON Input").outputLabel("CSV Output")
                .action("Convert to CSV")
                .download("converted.csv")
                .options(ToolOption.select("delimiter", "Delimiter", "comma",
                                "comma", "Comma", "semicolon", "Semicolon", "tab", "Tab", "pipe", "Pipe"),
                        ToolOption.toggle("header", "Include header row", true),
                        ToolOption.toggle("flatten", "Flatten nested objects", true)));

        add(Tool.of("csv-to-json", "CSV to JSON")
                .tagline("Turn delimited text into an array of objects")
                .description("Reads RFC 4180 CSV including quoted fields and embedded newlines, using the "
                        + "first row as keys.")
                .category(Category.CONVERT)
                .in(Editor.CSV).out(Editor.JSON)
                .inputLabel("CSV Input").outputLabel("JSON Output")
                .action("Convert to JSON")
                .download("converted.json")
                .options(ToolOption.indent(),
                        ToolOption.select("delimiter", "Delimiter", "comma",
                                "comma", "Comma", "semicolon", "Semicolon", "tab", "Tab", "pipe", "Pipe"),
                        ToolOption.toggle("header", "First row is the header", true),
                        ToolOption.toggle("coerce", "Detect numbers and booleans", true)));

        add(Tool.of("json-to-tsv", "JSON to TSV")
                .tagline("Flatten JSON into tab-separated values")
                .description("The same flattening as JSON to CSV, using tabs as the delimiter.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.CSV)
                .inputLabel("JSON Input").outputLabel("TSV Output")
                .action("Convert to TSV")
                .download("converted.tsv")
                .options(ToolOption.toggle("header", "Include header row", true),
                        ToolOption.toggle("flatten", "Flatten nested objects", true)));

        add(Tool.of("tsv-to-json", "TSV to JSON")
                .tagline("Turn tab-separated values into JSON")
                .description("Reads tab-delimited text using the first row as keys.")
                .category(Category.CONVERT)
                .in(Editor.CSV).out(Editor.JSON)
                .inputLabel("TSV Input").outputLabel("JSON Output")
                .action("Convert to JSON")
                .download("converted.json")
                .options(ToolOption.indent(),
                        ToolOption.toggle("header", "First row is the header", true),
                        ToolOption.toggle("coerce", "Detect numbers and booleans", true)));

        add(Tool.of("json-to-html", "JSON to HTML Table")
                .tagline("Render JSON as an HTML table")
                .description("Produces nested HTML tables - arrays of objects become a table with a header "
                        + "row, other objects become key/value rows. Preview it or copy the markup.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.TABLE)
                .inputLabel("JSON Input").outputLabel("HTML Table")
                .action("Convert to HTML")
                .download("table.html")
                .options(ToolOption.toggle("inlineCss", "Include inline CSS", true),
                        ToolOption.toggle("fullPage", "Wrap in a full HTML page", false)));

        add(Tool.of("json-to-excel", "JSON to Excel")
                .tagline("Download JSON as an .xlsx workbook")
                .description("Flattens the document the same way the CSV converter does and builds a real "
                        + "Excel workbook with a frozen, styled header row.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.CSV)
                .inputLabel("JSON Input").outputLabel("Sheet Preview")
                .action("Build Workbook")
                .download("data.xlsx")
                .options(ToolOption.text("sheetName", "Sheet name", "Sheet1", "Sheet1"),
                        ToolOption.toggle("flatten", "Flatten nested objects", true)));

        add(Tool.of("json-to-sql", "JSON to SQL")
                .tagline("Generate INSERT statements")
                .description("Emits INSERT statements for an array of objects, quoting text, escaping single "
                        + "quotes and writing NULL for nulls.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.SQL)
                .inputLabel("JSON Input").outputLabel("SQL Output")
                .action("Generate SQL")
                .download("insert.sql")
                .options(ToolOption.text("tableName", "Table name", "my_table", "my_table"),
                        ToolOption.select("dialect", "Quoting", "ansi",
                                "ansi", "ANSI (\"col\")", "mysql", "MySQL (`col`)",
                                "mssql", "SQL Server ([col])", "none", "No quoting"),
                        ToolOption.toggle("multiRow", "Single multi-row INSERT", false),
                        ToolOption.toggle("createTable", "Include CREATE TABLE", false)));

        add(Tool.of("json-to-string", "JSON to String")
                .tagline("Stringify JSON into a quoted literal")
                .description("Minifies the document and wraps it in a quoted, escaped string literal ready "
                        + "to paste into source code.")
                .category(Category.CONVERT)
                .in(Editor.JSON).out(Editor.TEXT)
                .inputLabel("JSON Input").outputLabel("String Literal")
                .action("Stringify")
                .download("string.txt"));

        add(Tool.of("string-to-json", "String to JSON")
                .tagline("Turn an escaped string literal back into JSON")
                .description("Unwraps a quoted string literal, resolves the escapes and pretty-prints the "
                        + "JSON it contained.")
                .category(Category.CONVERT)
                .in(Editor.TEXT).out(Editor.JSON)
                .inputLabel("String Literal").outputLabel("JSON Output")
                .action("Parse String")
                .download("parsed.json")
                .options(ToolOption.indent()));

        // ------------------------------------------------------------------
        // Code generation
        // ------------------------------------------------------------------
        add(Tool.of("json-to-java", "JSON to Java")
                .tagline("Generate POJOs from a sample document")
                .description("Infers a class per object - as records or as classes with getters and setters - "
                        + "widening types across every element of an array.")
                .category(Category.GENERATE)
                .in(Editor.JSON).out(Editor.CODE)
                .inputLabel("Sample JSON").outputLabel("Java")
                .action("Generate Java")
                .download("Root.java")
                .options(ToolOption.text("className", "Root class", "Root", "Root"),
                        ToolOption.text("packageName", "Package", "", "com.example.model"),
                        ToolOption.select("style", "Style", "class",
                                "class", "Class with getters/setters", "record", "Java record"),
                        ToolOption.toggle("jsonProperty", "Add @JsonProperty annotations", false)));

        add(Tool.of("json-to-typescript", "JSON to TypeScript")
                .tagline("Generate interfaces from a sample document")
                .description("Produces exported interfaces, marking keys that are missing from some array "
                        + "elements as optional.")
                .category(Category.GENERATE)
                .in(Editor.JSON).out(Editor.CODE)
                .inputLabel("Sample JSON").outputLabel("TypeScript")
                .action("Generate TypeScript")
                .download("model.ts")
                .options(ToolOption.text("className", "Root interface", "Root", "Root"),
                        ToolOption.select("style", "Style", "interface",
                                "interface", "interface", "type", "type alias")));

        add(Tool.of("json-to-csharp", "JSON to C#")
                .tagline("Generate C# classes from a sample document")
                .description("Produces classes with auto-properties, PascalCase names and JsonPropertyName "
                        + "attributes where the JSON key differs.")
                .category(Category.GENERATE)
                .in(Editor.JSON).out(Editor.CODE)
                .inputLabel("Sample JSON").outputLabel("C#")
                .action("Generate C#")
                .download("Root.cs")
                .options(ToolOption.text("className", "Root class", "Root", "Root"),
                        ToolOption.text("packageName", "Namespace", "", "Example.Models"),
                        ToolOption.toggle("jsonProperty", "Add [JsonPropertyName]", true)));

        add(Tool.of("json-to-python", "JSON to Python")
                .tagline("Generate dataclasses from a sample document")
                .description("Produces @dataclass definitions with type hints, using Optional for keys that "
                        + "are sometimes null or absent.")
                .category(Category.GENERATE)
                .in(Editor.JSON).out(Editor.CODE)
                .inputLabel("Sample JSON").outputLabel("Python")
                .action("Generate Python")
                .download("model.py")
                .options(ToolOption.text("className", "Root class", "Root", "Root")));

        add(Tool.of("json-to-go", "JSON to Go")
                .tagline("Generate structs from a sample document")
                .description("Produces Go structs with exported fields and matching json struct tags.")
                .category(Category.GENERATE)
                .in(Editor.JSON).out(Editor.CODE)
                .inputLabel("Sample JSON").outputLabel("Go")
                .action("Generate Go")
                .download("model.go")
                .options(ToolOption.text("className", "Root struct", "Root", "Root"),
                        ToolOption.text("packageName", "Package", "main", "main")));

        // ------------------------------------------------------------------
        // Text utilities
        // ------------------------------------------------------------------
        add(Tool.of("json-escape", "JSON Escape")
                .tagline("Escape text for embedding in JSON")
                .description("Escapes quotes, backslashes, newlines, tabs and control characters so the text "
                        + "can sit inside a JSON string.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Text Input").outputLabel("Escaped Text")
                .action("Escape")
                .download("escaped.txt")
                .options(ToolOption.toggle("quotes", "Wrap in double quotes", false),
                        ToolOption.toggle("escapeNonAscii", "Escape non-ASCII as \\uXXXX", false)));

        add(Tool.of("json-unescape", "JSON Unescape")
                .tagline("Resolve JSON escape sequences back to text")
                .description("Turns \\n, \\t, \\\" and \\uXXXX sequences back into the characters they stand for.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Escaped Text").outputLabel("Plain Text")
                .action("Unescape")
                .download("unescaped.txt"));

        add(Tool.of("base64-encode", "Base64 Encode")
                .tagline("Encode text as Base64")
                .description("UTF-8 encodes the text and Base64s it, with an optional URL-safe alphabet.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Text Input").outputLabel("Base64")
                .action("Encode")
                .download("encoded.txt")
                .options(ToolOption.toggle("urlSafe", "URL-safe alphabet", false),
                        ToolOption.toggle("padding", "Include padding", true)));

        add(Tool.of("base64-decode", "Base64 Decode")
                .tagline("Decode Base64 back to text")
                .description("Accepts both the standard and URL-safe alphabets, with or without padding.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Base64 Input").outputLabel("Decoded Text")
                .action("Decode")
                .download("decoded.txt"));

        add(Tool.of("url-encode", "URL Encode")
                .tagline("Percent-encode text for a URL")
                .description("Percent-encodes the text for use in a query string or path segment.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Text Input").outputLabel("Encoded Text")
                .action("Encode")
                .download("encoded.txt")
                .options(ToolOption.select("mode", "Mode", "query",
                        "query", "Query value (space as +)", "path", "Path segment (space as %20)")));

        add(Tool.of("url-decode", "URL Decode")
                .tagline("Resolve percent-encoding back to text")
                .description("Decodes %XX sequences, and + as a space in query mode.")
                .category(Category.UTILITY)
                .in(Editor.TEXT).out(Editor.TEXT)
                .inputLabel("Encoded Text").outputLabel("Decoded Text")
                .action("Decode")
                .download("decoded.txt")
                .options(ToolOption.select("mode", "Mode", "query",
                        "query", "Query value (+ is a space)", "path", "Path segment")));
    }

    private void add(Tool.Builder builder) {
        Tool tool = builder.build();
        tools.put(tool.getId(), tool);
    }

    public Optional<Tool> find(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public Tool require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /** Tools grouped by category, in registration order - drives the nav and home page. */
    public Map<Category, List<Tool>> byCategory() {
        Map<Category, List<Tool>> grouped = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            grouped.put(c, new ArrayList<>());
        }
        for (Tool t : tools.values()) {
            grouped.get(t.getCategory()).add(t);
        }
        return grouped;
    }
}
