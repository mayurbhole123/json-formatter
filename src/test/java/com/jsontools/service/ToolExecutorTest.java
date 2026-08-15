package com.jsontools.service;

import com.jsontools.model.Tool;
import com.jsontools.model.ToolOption;
import com.jsontools.model.ToolOptions;
import com.jsontools.model.ToolResult;
import com.jsontools.registry.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ToolExecutorTest {

    @Autowired
    private ToolExecutor executor;

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private SampleService samples;

    // ------------------------------------------------------------------
    // Every tool must run its own sample cleanly with its default options.
    // ------------------------------------------------------------------

    List<String> toolIds() {
        return registry.all().stream().map(Tool::getId).toList();
    }

    @ParameterizedTest(name = "{0} runs its sample")
    @MethodSource("toolIds")
    void everyToolHandlesItsOwnSample(String toolId) {
        Tool tool = registry.require(toolId);
        ToolResult result = executor.execute(toolId,
                samples.sampleFor(tool), samples.secondSampleFor(tool), defaults(tool));

        assertThat(result.isOk())
                .as("%s failed: %s", toolId, result.getError())
                .isTrue();
        assertThat(result.getOutput()).as("%s produced no output", toolId).isNotEmpty();
    }

    private static ToolOptions defaults(Tool tool) {
        Map<String, String> values = new HashMap<>();
        for (ToolOption option : tool.getOptions()) {
            values.put(option.getKey(), option.getDefaultValue());
        }
        return new ToolOptions(values);
    }

    private static ToolOptions options(String... keyValuePairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            values.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return new ToolOptions(values);
    }

    private String run(String toolId, String input, String... options) {
        ToolResult result = executor.execute(toolId, input, "", options(options));
        assertThat(result.isOk()).as("%s failed: %s", toolId, result.getError()).isTrue();
        return result.getOutput();
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    @Test
    @DisplayName("formatter indents with the requested width and keeps number precision")
    void formatter() {
        String output = run("json-formatter", "{\"a\":1,\"b\":[1,2],\"c\":1.20}", "indent", "4");
        assertThat(output).isEqualTo("""
                {
                    "a": 1,
                    "b": [
                        1,
                        2
                    ],
                    "c": 1.20
                }""");
    }

    @Test
    void formatterSortsKeysWhenAsked() {
        String output = run("json-formatter", "{\"b\":1,\"a\":2}", "indent", "2", "sortKeys", "true");
        assertThat(output).isEqualTo("{\n  \"a\": 2,\n  \"b\": 1\n}");
    }

    @Test
    void minifyStripsWhitespaceAndReportsSaving() {
        ToolResult result = executor.execute("json-minify", "{\n  \"a\" : 1\n}", "", ToolOptions.empty());
        assertThat(result.getOutput()).isEqualTo("{\"a\":1}");
        assertThat(result.getStats()).containsKey("Saved");
    }

    @Test
    @DisplayName("fixer repairs single quotes, unquoted keys, comments and trailing commas")
    void fixer() {
        String broken = "{ // note\n name: 'Ada', tags: ['x',], }";
        String output = run("json-fixer", broken, "indent", "2");
        assertThat(output).isEqualTo("{\n  \"name\": \"Ada\",\n  \"tags\": [\n    \"x\"\n  ]\n}");
    }

    @Test
    void sorterCanSortDescendingAndNonRecursively() {
        String output = run("json-sorter", "{\"b\":{\"z\":1,\"a\":2},\"a\":1}",
                "indent", "2", "direction", "desc", "recursive", "false");
        assertThat(output).startsWith("{\n  \"b\": {\n    \"z\": 1,");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void validatorReportsThePositionOfASyntaxError() {
        ToolResult result = executor.execute("json-validator", "{\"a\": }", "", ToolOptions.empty());
        assertThat(result.isOk()).isFalse();
        assertThat(result.getLine()).isEqualTo(1);
        assertThat(result.getColumn()).isGreaterThan(0);
    }

    @Test
    void validatorCountsNodes() {
        ToolResult result = executor.execute("json-validator", "{\"a\":[1,2]}", "", ToolOptions.empty());
        assertThat(result.isOk()).isTrue();
        assertThat(result.getStats()).containsEntry("Root", "Object (1 keys)");
    }

    @Test
    void schemaValidatorListsEveryViolation() {
        String data = "{\"name\": 5}";
        String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["name", "age"],
                  "properties": { "name": { "type": "string" } }
                }""";
        ToolResult result = executor.execute("json-schema-validator", data, schema, options("draft", "auto"));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getDetails()).hasSize(2);
        assertThat(result.getOutput()).contains("name").contains("age");
    }

    @Test
    void schemaGeneratorMarksSometimesMissingKeysAsOptional() {
        String output = run("json-schema-generator", "[{\"a\":1,\"b\":\"x\"},{\"a\":2}]",
                "indent", "2", "draft", "2020-12", "required", "true");
        assertThat(output).contains("\"$schema\"").contains("\"integer\"");
        // "b" is absent from the second element, so only "a" is required.
        assertThat(output).containsPattern("\"required\"\\s*:\\s*\\[\\s*\"a\"\\s*]");
    }

    @Test
    void jsonPathSelectsMatchingNodes() {
        String output = run("jsonpath-tester", "{\"a\":[{\"n\":1},{\"n\":2}]}", "path", "$.a[*].n", "indent", "2");
        assertThat(output.replaceAll("\\s", "")).isEqualTo("[1,2]");
    }

    // ------------------------------------------------------------------
    // Diff and merge
    // ------------------------------------------------------------------

    @Test
    void diffIgnoresKeyOrderAndReportsEveryChange() {
        ToolResult result = executor.execute("json-diff",
                "{\"a\":1,\"b\":2,\"gone\":true}",
                "{\"b\":2,\"a\":9,\"added\":\"x\"}",
                ToolOptions.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(result.getDetails()).hasSize(3);
        assertThat(result.getStats()).containsEntry("Differences", "3");
        assertThat(result.getDetails())
                .anySatisfy(row -> assertThat(row).containsEntry("path", "$.a").containsEntry("type", "Changed"))
                .anySatisfy(row -> assertThat(row).containsEntry("path", "$.gone").containsEntry("type", "Removed"))
                .anySatisfy(row -> assertThat(row).containsEntry("path", "$.added").containsEntry("type", "Added"));
    }

    @Test
    void diffTreatsOneAndOnePointZeroAsEqual() {
        ToolResult result = executor.execute("json-diff", "{\"n\":1}", "{\"n\":1.0}", ToolOptions.empty());
        assertThat(result.getDetails()).isEmpty();
    }

    @Test
    void diffCanIgnoreArrayOrder() {
        ToolResult ordered = executor.execute("json-diff", "[1,2]", "[2,1]", ToolOptions.empty());
        assertThat(ordered.getDetails()).isNotEmpty();

        ToolResult unordered = executor.execute("json-diff", "[1,2]", "[2,1]",
                options("ignoreArrayOrder", "true"));
        assertThat(unordered.getDetails()).isEmpty();
    }

    @Test
    void mergeCombinesObjectsRecursively() {
        ToolResult result = executor.execute("json-merge",
                "{\"a\":{\"x\":1},\"keep\":true}", "{\"a\":{\"y\":2}}",
                options("indent", "2", "arrays", "replace"));
        assertThat(result.getOutput().replaceAll("\\s", ""))
                .isEqualTo("{\"a\":{\"x\":1,\"y\":2},\"keep\":true}");
    }

    // ------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------

    @Test
    void jsonToCsvFlattensNestedObjectsAndUnionsTheHeader() {
        String output = run("json-to-csv",
                "[{\"id\":1,\"who\":{\"name\":\"Ada\"}},{\"id\":2,\"extra\":true}]",
                "delimiter", "comma", "header", "true", "flatten", "true");
        assertThat(output).isEqualTo("""
                id,who.name,extra
                1,Ada,
                2,,true""");
    }

    @Test
    void jsonToCsvQuotesCellsContainingTheDelimiter() {
        String output = run("json-to-csv", "[{\"a\":\"x,y\"}]", "delimiter", "comma", "header", "true");
        assertThat(output).isEqualTo("a\n\"x,y\"");
    }

    @Test
    void csvToJsonUsesTheHeaderAndDetectsTypes() {
        String output = run("csv-to-json", "id,name,ok\n1,Ada,true\n007,Bob,false",
                "indent", "2", "delimiter", "comma", "header", "true", "coerce", "true");
        assertThat(output.replaceAll("\\s", ""))
                .isEqualTo("[{\"id\":1,\"name\":\"Ada\",\"ok\":true},{\"id\":\"007\",\"name\":\"Bob\",\"ok\":false}]");
    }

    @Test
    void csvToJsonHandlesQuotedFieldsWithEmbeddedCommas() {
        String output = run("csv-to-json", "a,b\n\"x,y\",2",
                "indent", "2", "delimiter", "comma", "header", "true", "coerce", "true");
        assertThat(output.replaceAll("\\s", "")).isEqualTo("[{\"a\":\"x,y\",\"b\":2}]");
    }

    @Test
    void jsonToXmlRepeatsTheItemElementAndEscapesText() {
        String output = run("json-to-xml", "{\"tags\":[\"a\",\"b<c\"]}",
                "indent", "2", "rootName", "root", "itemName", "item", "declaration", "false");
        assertThat(output).isEqualTo("""
                <root>
                  <tags>
                    <item>a</item>
                    <item>b&lt;c</item>
                  </tags>
                </root>""");
    }

    @Test
    void jsonToXmlSanitisesKeysThatAreNotLegalElementNames() {
        String output = run("json-to-xml", "{\"2bad key\":1}",
                "indent", "2", "rootName", "root", "declaration", "false");
        assertThat(output).contains("<_2bad_key>1</_2bad_key>");
    }

    @Test
    void xmlToJsonGroupsRepeatedSiblingsIntoAnArray() {
        String output = run("xml-to-json",
                "<r><i>1</i><i>2</i></r>",
                "indent", "2", "attributes", "true", "coerce", "false");
        assertThat(output.replaceAll("\\s", "")).isEqualTo("{\"r\":{\"i\":[\"1\",\"2\"]}}");
    }

    @Test
    void xmlToJsonKeepsAttributesAndCanCoerceTypes() {
        String output = run("xml-to-json",
                "<r><i id=\"7\">yes</i></r>",
                "indent", "2", "attributes", "true", "coerce", "true");
        assertThat(output.replaceAll("\\s", "")).isEqualTo("{\"r\":{\"i\":{\"@id\":7,\"#text\":\"yes\"}}}");
    }

    @Test
    void yamlRoundTripsThroughJson() {
        String yaml = run("json-to-yaml", "{\"a\":1,\"b\":[\"x\"]}");
        String backToJson = run("yaml-to-json", yaml, "indent", "2");
        assertThat(backToJson.replaceAll("\\s", "")).isEqualTo("{\"a\":1,\"b\":[\"x\"]}");
    }

    @Test
    void jsonToSqlQuotesTextAndLeavesNumbersBare() {
        String output = run("json-to-sql", "[{\"id\":1,\"name\":\"O'Hara\",\"gone\":null}]",
                "tableName", "people", "dialect", "ansi", "multiRow", "false", "createTable", "false");
        assertThat(output).isEqualTo(
                "INSERT INTO \"people\" (\"id\", \"name\", \"gone\") VALUES (1, 'O''Hara', NULL);");
    }

    @Test
    void jsonToSqlCanEmitOneMultiRowStatement() {
        String output = run("json-to-sql", "[{\"a\":1},{\"a\":2}]",
                "tableName", "t", "dialect", "mysql", "multiRow", "true");
        assertThat(output).isEqualTo("INSERT INTO `t` (`a`)\nVALUES\n  (1),\n  (2);");
    }

    @Test
    void jsonToHtmlBuildsATableWithAHeaderRow() {
        String output = run("json-to-html", "[{\"a\":1},{\"a\":2}]", "inlineCss", "false", "fullPage", "false");
        assertThat(output).startsWith("<table class=\"json-table\"><thead><tr><th>a</th>");
    }

    @Test
    void jsonToHtmlEscapesMarkupInValues() {
        String output = run("json-to-html", "{\"a\":\"<script>\"}", "inlineCss", "false");
        assertThat(output).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void stringifyAndParseRoundTrip() {
        String literal = run("json-to-string", "{\"a\":\"x\\\"y\"}");
        assertThat(literal).startsWith("\"{").endsWith("}\"");
        String back = run("string-to-json", literal, "indent", "2");
        assertThat(back.replaceAll("\\s", "")).isEqualTo("{\"a\":\"x\\\"y\"}");
    }

    // ------------------------------------------------------------------
    // Code generation
    // ------------------------------------------------------------------

    @Test
    void javaGeneratorNestsClassesAndWidensTypesAcrossArrayElements() {
        String output = run("json-to-java",
                "{\"id\":1,\"ratio\":1,\"items\":[{\"n\":\"a\"},{\"n\":\"b\",\"extra\":2.5}]}",
                "className", "Root", "packageName", "com.example", "style", "class");

        assertThat(output).startsWith("package com.example;");
        assertThat(output).contains("import java.util.List;");
        assertThat(output).contains("public class Root {");
        assertThat(output).contains("private List<Item> items;");
        assertThat(output).contains("public static class Item {");
        assertThat(output).contains("private Double extra;");
        assertThat(output).contains("public List<Item> getItems()");
        // Exactly one closing brace per class, and the file is balanced.
        assertThat(countChar(output, '{')).isEqualTo(countChar(output, '}'));
    }

    @Test
    void javaGeneratorCanEmitRecords() {
        String output = run("json-to-java", "{\"a\":1,\"b\":{\"c\":\"x\"}}",
                "className", "Root", "style", "record");
        assertThat(output).contains("public record Root(");
        assertThat(output).contains("public record B(");
        assertThat(countChar(output, '{')).isEqualTo(countChar(output, '}'));
    }

    @Test
    void javaGeneratorEscapesKeysThatAreNotLegalIdentifiers() {
        String output = run("json-to-java", "{\"my-key\":1,\"class\":2}", "className", "Root", "style", "class");
        assertThat(output).contains("@JsonProperty(\"my-key\")").contains("private Long myKey;");
        assertThat(output).contains("private Long classValue;");
    }

    @Test
    void typeScriptMarksSometimesAbsentKeysAsOptional() {
        String output = run("json-to-typescript", "[{\"a\":1},{\"a\":2,\"b\":\"x\"}]",
                "className", "Row", "style", "interface");
        assertThat(output).contains("export interface Row {");
        assertThat(output).contains("a: number;");
        assertThat(output).contains("b?: string;");
    }

    @Test
    void csharpAddsNullableMarkersOnlyToValueTypes() {
        String output = run("json-to-csharp", "[{\"a\":1},{\"b\":\"x\"}]",
                "className", "Row", "packageName", "Demo", "jsonProperty", "true");
        assertThat(output).contains("namespace Demo");
        assertThat(output).contains("[JsonPropertyName(\"a\")]");
        assertThat(output).contains("public long? A { get; set; }");
        assertThat(output).contains("public string B { get; set; }");
    }

    @Test
    void pythonPutsFieldsWithDefaultsLast() {
        String output = run("json-to-python", "[{\"a\":1},{\"a\":2,\"b\":\"x\"}]", "className", "Row");
        assertThat(output).contains("@dataclass");
        int required = output.indexOf("a: int");
        int optional = output.indexOf("b: Optional[str] = None");
        assertThat(required).isGreaterThan(-1);
        assertThat(optional).isGreaterThan(required);
    }

    @Test
    void goAddsJsonTags() {
        String output = run("json-to-go", "{\"user_name\":\"a\",\"tags\":[\"x\"]}",
                "className", "Root", "packageName", "model");
        assertThat(output).startsWith("package model");
        assertThat(output).contains("UserName string `json:\"user_name\"`");
        assertThat(output).contains("Tags []string `json:\"tags\"`");
    }

    // ------------------------------------------------------------------
    // Text utilities
    // ------------------------------------------------------------------

    @Test
    void escapeAndUnescapeRoundTrip() {
        String original = "line1\nline2\t\"quoted\" \\ backslash";
        String escaped = run("json-escape", original);
        assertThat(escaped).isEqualTo("line1\\nline2\\t\\\"quoted\\\" \\\\ backslash");
        assertThat(run("json-unescape", escaped)).isEqualTo(original);
    }

    @Test
    void unescapeRejectsAnUnknownSequence() {
        ToolResult result = executor.execute("json-unescape", "bad \\q here", "", ToolOptions.empty());
        assertThat(result.isOk()).isFalse();
        assertThat(result.getError()).contains("\\q");
    }

    @Test
    void base64RoundTripsIncludingNonAsciiText() {
        String encoded = run("base64-encode", "héllo wörld", "urlSafe", "false", "padding", "true");
        assertThat(run("base64-decode", encoded)).isEqualTo("héllo wörld");
    }

    @Test
    void base64DecodeAcceptsUrlSafeInputWithoutPadding() {
        String encoded = run("base64-encode", "??>>??", "urlSafe", "true", "padding", "false");
        assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(run("base64-decode", encoded)).isEqualTo("??>>??");
    }

    @Test
    void urlEncodeDistinguishesQueryFromPathMode() {
        assertThat(run("url-encode", "a b", "mode", "query")).isEqualTo("a+b");
        assertThat(run("url-encode", "a b", "mode", "path")).isEqualTo("a%20b");
        assertThat(run("url-decode", "a+b", "mode", "query")).isEqualTo("a b");
        assertThat(run("url-decode", "a+b", "mode", "path")).isEqualTo("a+b");
    }

    // ------------------------------------------------------------------
    // Failure handling
    // ------------------------------------------------------------------

    @Test
    void anUnknownToolIsReportedRatherThanThrown() {
        ToolResult result = executor.execute("nope", "{}", "", ToolOptions.empty());
        assertThat(result.isOk()).isFalse();
        assertThat(result.getError()).contains("Unknown tool");
    }

    @Test
    void emptyInputProducesAHelpfulMessage() {
        ToolResult result = executor.execute("json-formatter", "   ", "", ToolOptions.empty());
        assertThat(result.isOk()).isFalse();
        assertThat(result.getError()).contains("No input");
    }

    @Test
    void dualInputToolsAskForTheSecondPane() {
        ToolResult result = executor.execute("json-diff", "{}", "", ToolOptions.empty());
        assertThat(result.isOk()).isFalse();
        assertThat(result.getError()).contains("second document");
    }

    @Test
    void excelWorkbookIsAValidZipContainer() throws Exception {
        byte[] workbook = executor.excelWorkbook("[{\"a\":1,\"b\":\"x\"}]", options("sheetName", "Data"));
        assertThat(workbook).isNotEmpty();
        // Every .xlsx is a ZIP, which always starts with "PK".
        assertThat(workbook[0]).isEqualTo((byte) 'P');
        assertThat(workbook[1]).isEqualTo((byte) 'K');
    }

    private static long countChar(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }
}
