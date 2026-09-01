package com.jsontools.model;

import java.util.List;
import java.util.Map;

/**
 * What every tool hands back: the produced text, or a failure message with the
 * parser's error position when there was one.
 *
 * <p>A class rather than a record so the templates and Jackson both see plain
 * JavaBean getters. Jackson serialises those getters, so the API sees
 * {@code ok, output, error, line, column, stats, details}.
 */
public final class ToolResult {

    private final boolean ok;
    private final String output;
    private final String error;
    private final int line;
    private final int column;
    private final Map<String, String> stats;
    private final List<Map<String, String>> details;

    public ToolResult(boolean ok, String output, String error, int line, int column,
                      Map<String, String> stats, List<Map<String, String>> details) {
        this.ok = ok;
        this.output = output;
        this.error = error;
        this.line = line;
        this.column = column;
        this.stats = stats == null ? Map.of() : stats;
        this.details = details == null ? List.of() : details;
    }

    public static ToolResult ok(String output) {
        return new ToolResult(true, output, null, 0, 0, Map.of(), List.of());
    }

    public static ToolResult ok(String output, Map<String, String> stats) {
        return new ToolResult(true, output, null, 0, 0, stats, List.of());
    }

    public static ToolResult ok(String output, Map<String, String> stats, List<Map<String, String>> details) {
        return new ToolResult(true, output, null, 0, 0, stats, details);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, "", message, 0, 0, Map.of(), List.of());
    }

    public static ToolResult error(String message, int line, int column) {
        return new ToolResult(false, "", message, line, column, Map.of(), List.of());
    }

    public boolean isOk()                        { return ok; }
    public String getOutput()                    { return output; }
    public String getError()                     { return error; }
    public int getLine()                         { return line; }
    public int getColumn()                       { return column; }
    public Map<String, String> getStats()        { return stats; }
    public List<Map<String, String>> getDetails() { return details; }

    /** True when the parser told us where the problem is. */
    public boolean isPositioned() {
        return line > 0;
    }

    public boolean isHasStats() {
        return !stats.isEmpty();
    }

    public boolean isHasDetails() {
        return !details.isEmpty();
    }
}
