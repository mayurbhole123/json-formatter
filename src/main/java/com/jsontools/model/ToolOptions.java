package com.jsontools.model;

import java.util.Map;

/** Typed accessor over the option values a tool form submits. */
public record ToolOptions(Map<String, String> values) {

    public ToolOptions {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ToolOptions empty() {
        return new ToolOptions(Map.of());
    }

    public String get(String key, String fallback) {
        String v = values.get(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public String get(String key) {
        return get(key, null);
    }

    /**
     * Checkboxes are absent from the request when unticked, so a missing key
     * falls back to the caller's default rather than to {@code false}.
     */
    public boolean flag(String key, boolean fallback) {
        String v = values.get(key);
        if (v == null || v.isEmpty()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v) || "1".equals(v);
    }

    public int number(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String indent() {
        return get("indent", "2");
    }
}
