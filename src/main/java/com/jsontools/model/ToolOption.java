package com.jsontools.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One control rendered into a tool's option bar. The {@code key} is the name the
 * value arrives under in {@link ToolOptions}.
 *
 * <p>A class rather than a record because JSP's expression language resolves
 * JavaBean getters, not record accessors.
 */
public final class ToolOption {

    public enum Type { SELECT, TOGGLE, TEXT }

    /** One entry of a SELECT control. */
    public static final class Choice {
        private final String value;
        private final String label;

        public Choice(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
    }

    private final String key;
    private final String label;
    private final Type type;
    private final String defaultValue;
    private final List<Choice> choices;
    private final String placeholder;

    private ToolOption(String key, String label, Type type, String defaultValue,
                       List<Choice> choices, String placeholder) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.choices = choices;
        this.placeholder = placeholder;
    }

    public static ToolOption select(String key, String label, String defaultValue, String... valueLabelPairs) {
        if (valueLabelPairs.length % 2 != 0) {
            throw new IllegalArgumentException("select() needs value/label pairs, got " + valueLabelPairs.length);
        }
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < valueLabelPairs.length; i += 2) {
            choices.add(new Choice(valueLabelPairs[i], valueLabelPairs[i + 1]));
        }
        return new ToolOption(key, label, Type.SELECT, defaultValue, List.copyOf(choices), null);
    }

    public static ToolOption toggle(String key, String label, boolean defaultValue) {
        return new ToolOption(key, label, Type.TOGGLE, Boolean.toString(defaultValue), List.of(), null);
    }

    public static ToolOption text(String key, String label, String defaultValue, String placeholder) {
        return new ToolOption(key, label, Type.TEXT, defaultValue, List.of(), placeholder);
    }

    /** The standard indent picker, shared by every tool that prints JSON. */
    public static ToolOption indent() {
        return select("indent", "Indent", "2",
                "2", "2 spaces",
                "3", "3 spaces",
                "4", "4 spaces",
                "tab", "Tab");
    }

    public String getKey()          { return key; }
    public String getLabel()        { return label; }
    public Type getType()           { return type; }
    public String getDefaultValue() { return defaultValue; }
    public List<Choice> getChoices() { return choices; }
    public String getPlaceholder()  { return placeholder; }

    // EL cannot compare enum constants conveniently, so expose the discriminators.
    public boolean isSelect()  { return type == Type.SELECT; }
    public boolean isToggle()  { return type == Type.TOGGLE; }
    public boolean isText()    { return type == Type.TEXT; }
    public boolean isChecked() { return Boolean.parseBoolean(defaultValue); }
}
