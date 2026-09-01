package com.jsontools.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a single tool. The registry holds one of these per tool and the
 * generic tool page renders itself entirely from it, so adding a tool is a
 * registry entry plus a branch in the executor.
 *
 * <p>A class rather than a record so the templates and Jackson both see plain
 * JavaBean getters.
 */
public final class Tool {

    /** How a pane behaves: which syntax it holds and how a result is presented. */
    public enum Editor {
        /** Editable text panes. */
        JSON, XML, YAML, CSV, TEXT, SQL, HTML, CODE,
        /** Read-only presentations produced by some tools. */
        TREE, TABLE, DIFF, REPORT
    }

    public enum Category {
        FORMAT("Format & Beautify"),
        VALIDATE("Validate & Query"),
        CONVERT("Convert"),
        GENERATE("Generate Code"),
        UTILITY("Text Utilities");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String id;
    private final String title;
    private final String tagline;
    private final String description;
    private final Category category;
    private final Editor inputEditor;
    private final Editor outputEditor;
    private final String inputLabel;
    private final String outputLabel;
    private final String secondInputLabel;
    private final String actionLabel;
    private final String downloadName;
    private final List<ToolOption> options;

    private Tool(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.tagline = b.tagline;
        this.description = b.description;
        this.category = b.category;
        this.inputEditor = b.inputEditor;
        this.outputEditor = b.outputEditor;
        this.inputLabel = b.inputLabel;
        this.outputLabel = b.outputLabel;
        this.secondInputLabel = b.secondInputLabel;
        this.actionLabel = b.actionLabel;
        this.downloadName = b.downloadName;
        this.options = List.copyOf(b.options);
    }

    public String getId()               { return id; }
    public String getTitle()            { return title; }
    public String getTagline()          { return tagline; }
    public String getDescription()      { return description; }
    public Category getCategory()       { return category; }
    public Editor getInputEditor()      { return inputEditor; }
    public Editor getOutputEditor()     { return outputEditor; }
    public String getInputLabel()       { return inputLabel; }
    public String getOutputLabel()      { return outputLabel; }
    public String getSecondInputLabel() { return secondInputLabel; }
    public String getActionLabel()      { return actionLabel; }
    public String getDownloadName()     { return downloadName; }
    public List<ToolOption> getOptions() { return options; }

    public String getUrl() {
        return "/" + id;
    }

    /** True when the tool takes a second input pane (diff, merge, schema validation). */
    public boolean isDualInput() {
        return secondInputLabel != null;
    }

    public boolean isDownloadable() {
        return downloadName != null;
    }

    /** True for the one tool whose download has to be built on the server. */
    public boolean isBinaryDownload() {
        return "json-to-excel".equals(id);
    }

    // View flags for the output pane.
    public boolean isTreeOutput()   { return outputEditor == Editor.TREE; }
    public boolean isTableOutput()  { return outputEditor == Editor.TABLE; }
    public boolean isDiffOutput()   { return outputEditor == Editor.DIFF; }
    public boolean isReportOutput() { return outputEditor == Editor.REPORT; }

    /** Syntax-highlighting hint handed to the client editor. */
    public String getInputSyntax()  { return inputEditor.name().toLowerCase(); }
    public String getOutputSyntax() { return outputEditor.name().toLowerCase(); }

    public static Builder of(String id, String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private String tagline = "";
        private String description = "";
        private Category category = Category.FORMAT;
        private Editor inputEditor = Editor.JSON;
        private Editor outputEditor = Editor.JSON;
        private String inputLabel = "Input";
        private String outputLabel = "Output";
        private String secondInputLabel;
        private String actionLabel = "Run";
        private String downloadName;
        private final List<ToolOption> options = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder tagline(String v)      { this.tagline = v; return this; }
        public Builder description(String v)  { this.description = v; return this; }
        public Builder category(Category v)   { this.category = v; return this; }
        public Builder in(Editor v)           { this.inputEditor = v; return this; }
        public Builder out(Editor v)          { this.outputEditor = v; return this; }
        public Builder inputLabel(String v)   { this.inputLabel = v; return this; }
        public Builder outputLabel(String v)  { this.outputLabel = v; return this; }
        public Builder secondInput(String v)  { this.secondInputLabel = v; return this; }
        public Builder action(String v)       { this.actionLabel = v; return this; }
        public Builder download(String v)     { this.downloadName = v; return this; }

        public Builder options(ToolOption... v) {
            this.options.addAll(List.of(v));
            return this;
        }

        public Tool build() {
            return new Tool(this);
        }
    }
}
