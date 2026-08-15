package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Structural comparison and deep merge of two JSON documents. */
@Service
public class DiffService {

    public enum ChangeType {
        ADDED("Added"), REMOVED("Removed"), CHANGED("Changed"), TYPE_CHANGED("Type changed");

        private final String label;

        ChangeType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** One difference, addressed by a JSONPath-style path. */
    public record Difference(String path, ChangeType type, String left, String right) {}

    public record Options(boolean ignoreArrayOrder, boolean ignoreCase) {}

    private final JsonService json;

    public DiffService(JsonService json) {
        this.json = json;
    }

    public List<Difference> diff(JsonNode left, JsonNode right, Options options) {
        List<Difference> out = new ArrayList<>();
        compare("$", left, right, options, out);
        return out;
    }

    private void compare(String path, JsonNode left, JsonNode right, Options options, List<Difference> out) {
        if (left.isObject() && right.isObject()) {
            compareObjects(path, left, right, options, out);
            return;
        }
        if (left.isArray() && right.isArray()) {
            compareArrays(path, left, right, options, out);
            return;
        }
        if (nodeType(left) != nodeType(right)) {
            out.add(new Difference(path, ChangeType.TYPE_CHANGED, render(left), render(right)));
            return;
        }
        if (!equalValues(left, right, options)) {
            out.add(new Difference(path, ChangeType.CHANGED, render(left), render(right)));
        }
    }

    private void compareObjects(String path, JsonNode left, JsonNode right, Options options, List<Difference> out) {
        // Key order is not meaningful in JSON, so walk the union of both key sets.
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        left.fieldNames().forEachRemaining(keys::add);
        right.fieldNames().forEachRemaining(keys::add);

        for (String key : keys) {
            String childPath = path + childAccessor(key);
            JsonNode l = left.get(key);
            JsonNode r = right.get(key);
            if (l == null) {
                out.add(new Difference(childPath, ChangeType.ADDED, "", render(r)));
            } else if (r == null) {
                out.add(new Difference(childPath, ChangeType.REMOVED, render(l), ""));
            } else {
                compare(childPath, l, r, options, out);
            }
        }
    }

    private void compareArrays(String path, JsonNode left, JsonNode right, Options options, List<Difference> out) {
        if (options.ignoreArrayOrder()) {
            compareArraysUnordered(path, left, right, options, out);
            return;
        }
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            compare(path + "[" + i + "]", left.get(i), right.get(i), options, out);
        }
        for (int i = shared; i < left.size(); i++) {
            out.add(new Difference(path + "[" + i + "]", ChangeType.REMOVED, render(left.get(i)), ""));
        }
        for (int i = shared; i < right.size(); i++) {
            out.add(new Difference(path + "[" + i + "]", ChangeType.ADDED, "", render(right.get(i))));
        }
    }

    /**
     * Order-insensitive comparison: pair each left element with an equal, not yet
     * consumed right element, then report whatever is left over on either side.
     */
    private void compareArraysUnordered(String path, JsonNode left, JsonNode right,
                                        Options options, List<Difference> out) {
        boolean[] matched = new boolean[right.size()];
        List<Integer> unmatchedLeft = new ArrayList<>();

        for (int i = 0; i < left.size(); i++) {
            boolean found = false;
            for (int j = 0; j < right.size(); j++) {
                if (!matched[j] && deepEquals(left.get(i), right.get(j), options)) {
                    matched[j] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                unmatchedLeft.add(i);
            }
        }
        for (int i : unmatchedLeft) {
            out.add(new Difference(path + "[" + i + "]", ChangeType.REMOVED, render(left.get(i)), ""));
        }
        for (int j = 0; j < right.size(); j++) {
            if (!matched[j]) {
                out.add(new Difference(path + "[" + j + "]", ChangeType.ADDED, "", render(right.get(j))));
            }
        }
    }

    private boolean deepEquals(JsonNode a, JsonNode b, Options options) {
        List<Difference> scratch = new ArrayList<>();
        compare("$", a, b, options, scratch);
        return scratch.isEmpty();
    }

    private boolean equalValues(JsonNode left, JsonNode right, Options options) {
        if (left.isNumber() && right.isNumber()) {
            // 1 and 1.0 are the same number even though the nodes differ.
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        if (left.isTextual() && right.isTextual() && options.ignoreCase()) {
            return left.asText().equalsIgnoreCase(right.asText());
        }
        return left.equals(right);
    }

    /** Coarse type so that 1 vs "1" reads as a type change, not a value change. */
    private String nodeType(JsonNode node) {
        if (node.isNumber()) return "number";
        if (node.isTextual()) return "string";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "unknown";
    }

    private String childAccessor(String key) {
        return Names.isPlainIdentifier(key) ? "." + key : "['" + key.replace("'", "\\'") + "']";
    }

    /** Compact one-line rendering, truncated so a diff row stays readable. */
    private String render(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        String text = node.isTextual() ? node.asText() : node.toString();
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    // ==================================================================
    // Merge
    // ==================================================================

    public record MergeOptions(boolean concatArrays, boolean nullRemoves) {}

    /** Deep-merges {@code patch} into {@code base}, returning a new document. */
    public JsonNode merge(JsonNode base, JsonNode patch, MergeOptions options) {
        if (base.isObject() && patch.isObject()) {
            ObjectNode out = base.deepCopy();
            for (Map.Entry<String, JsonNode> field : patch.properties()) {
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (value.isNull() && options.nullRemoves()) {
                    out.remove(key);
                } else if (out.has(key)) {
                    out.set(key, merge(out.get(key), value, options));
                } else {
                    out.set(key, value.deepCopy());
                }
            }
            return out;
        }
        if (base.isArray() && patch.isArray() && options.concatArrays()) {
            return json.mapper().createArrayNode().addAll((com.fasterxml.jackson.databind.node.ArrayNode) base)
                    .addAll((com.fasterxml.jackson.databind.node.ArrayNode) patch);
        }
        // Anything else: the patch wins.
        return patch.deepCopy();
    }

    /** Human-readable summary line for the diff report. */
    public String summarise(List<Difference> differences) {
        if (differences.isEmpty()) {
            return "The two documents are structurally identical.";
        }
        long added = differences.stream().filter(d -> d.type() == ChangeType.ADDED).count();
        long removed = differences.stream().filter(d -> d.type() == ChangeType.REMOVED).count();
        long changed = differences.size() - added - removed;
        return String.format(Locale.ROOT,
                "%d difference%s: %d added, %d removed, %d changed.",
                differences.size(), differences.size() == 1 ? "" : "s", added, removed, changed);
    }
}
