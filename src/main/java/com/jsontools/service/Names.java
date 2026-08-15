package com.jsontools.service;

import java.util.Locale;

/** Identifier munging shared by the code generators. */
public final class Names {

    private Names() {
    }

    /** "user_name", "user-name", "user name" -> "UserName". */
    public static String pascal(String raw) {
        StringBuilder out = new StringBuilder();
        boolean upperNext = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (out.isEmpty() && Character.isDigit(c)) {
                    out.append('_');
                }
                out.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }
        return out.isEmpty() ? "Value" : out.toString();
    }

    /** "user_name" -> "userName". */
    public static String camel(String raw) {
        String p = pascal(raw);
        return Character.toLowerCase(p.charAt(0)) + p.substring(1);
    }

    /** "user_name" -> "user_name", sanitised to a legal snake_case identifier. */
    public static String snake(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (Character.isUpperCase(c) && !out.isEmpty() && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        String s = out.toString();
        if (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return "value";
        }
        return Character.isDigit(s.charAt(0)) ? "_" + s : s;
    }

    /**
     * Best-effort English singular, used to name the class behind an array field
     * ("addresses" -> "Address").
     */
    public static String singular(String word) {
        String w = word;
        String lower = w.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && w.length() > 3) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
                || lower.endsWith("ches") || lower.endsWith("shes")) {
            return w.substring(0, w.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && w.length() > 1) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** True when the JSON key can be used verbatim as a Java/C#/Go/TS identifier. */
    public static boolean isPlainIdentifier(String raw) {
        if (raw == null || raw.isEmpty() || !Character.isJavaIdentifierStart(raw.charAt(0))) {
            return false;
        }
        for (int i = 1; i < raw.length(); i++) {
            if (!Character.isJavaIdentifierPart(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
