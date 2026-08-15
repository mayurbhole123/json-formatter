package com.jsontools.service;

import com.jsontools.model.TypeNode;
import com.jsontools.model.TypeNode.Kind;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders an inferred {@link TypeNode} tree as source code. Every generator
 * walks the same class list, so Java, TypeScript, C#, Python and Go all agree on
 * which types exist and what they are called.
 *
 * <p>All per-generation state lives in a {@link Model} passed down the call
 * chain - the service itself is stateless and safe to share across requests.
 */
@Service
public class CodeGenService {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while");

    private static final Set<String> CSHARP_KEYWORDS = Set.of(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "class",
            "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event",
            "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "goto", "if",
            "implicit", "in", "int", "interface", "internal", "is", "lock", "long", "namespace", "new",
            "null", "object", "operator", "out", "override", "params", "private", "protected", "public",
            "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof", "static", "string", "struct",
            "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe",
            "ushort", "using", "virtual", "void", "volatile", "while");

    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
            "else", "except", "false", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "none", "nonlocal", "not", "or", "pass", "raise", "return", "true", "try", "while",
            "with", "yield");

    private static final Set<String> GO_KEYWORDS = Set.of(
            "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for",
            "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select",
            "struct", "switch", "type", "var");

    /** One object type that will be emitted as a class / interface / struct. */
    private record ClassDef(String name, TypeNode type) {}

    /** The named class list for a single generation. */
    private record Model(List<ClassDef> classes, Map<TypeNode, String> names) {

        String nameOf(TypeNode node, String fallback) {
            return names.getOrDefault(node, fallback);
        }

        boolean usesArray() {
            for (ClassDef def : classes) {
                for (TypeNode field : def.type().fields().values()) {
                    if (field.isArray()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Assigns a unique name to every object type in the tree, breadth-first, so
     * classes appear in the order a reader meets them.
     */
    private Model buildModel(TypeNode root, String rootName) {
        List<ClassDef> classes = new ArrayList<>();
        Map<TypeNode, String> names = new IdentityHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        Deque<TypeNode> queue = new ArrayDeque<>();

        TypeNode start = unwrapArrays(root);
        if (!start.isObject()) {
            return new Model(List.of(), Map.of());
        }
        names.put(start, claim(Names.pascal(rootName), used));
        queue.add(start);

        while (!queue.isEmpty()) {
            TypeNode current = queue.poll();
            classes.add(new ClassDef(names.get(current), current));
            for (Map.Entry<String, TypeNode> field : current.fields().entrySet()) {
                TypeNode value = unwrapArrays(field.getValue());
                if (value.isObject() && !names.containsKey(value)) {
                    names.put(value, claim(Names.pascal(Names.singular(field.getKey())), used));
                    queue.add(value);
                }
            }
        }
        return new Model(classes, names);
    }

    private Model requireModel(TypeNode root, String rootName, String what) {
        Model model = buildModel(root, rootName);
        if (model.classes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Provide a JSON object (or an array of objects) to generate " + what + " from.");
        }
        return model;
    }

    private static TypeNode unwrapArrays(TypeNode node) {
        TypeNode current = node;
        while (current != null && current.isArray()) {
            current = current.element();
        }
        return current == null ? TypeNode.of(Kind.ANY) : current;
    }

    private static String claim(String preferred, Set<String> used) {
        String candidate = preferred;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = preferred + suffix++;
        }
        return candidate;
    }

    private static String safeName(String candidate, Set<String> keywords, String suffix) {
        return keywords.contains(candidate.toLowerCase()) ? candidate + suffix : candidate;
    }

    // ==================================================================
    // Java
    // ==================================================================

    /**
     * Java allows one public top-level type per file, so the root type is public
     * and every other type is emitted as a member of it.
     */
    public String toJava(TypeNode root, String rootName, String packageName,
                         boolean asRecord, boolean jsonProperty) {
        Model model = requireModel(root, rootName, "classes");
        StringBuilder sb = new StringBuilder();

        if (packageName != null && !packageName.isBlank()) {
            sb.append("package ").append(packageName.trim()).append(";\n\n");
        }
        boolean needsList = model.usesArray();
        // An annotation is also emitted when a key cannot be spelled as a Java
        // field name, so the import is needed whenever either is true.
        boolean needsAnnotation = jsonProperty || hasRenamedField(model);
        if (needsList) {
            sb.append("import java.util.List;\n");
        }
        if (needsAnnotation) {
            sb.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        }
        if (needsList || needsAnnotation) {
            sb.append('\n');
        }

        ClassDef rootDef = model.classes().get(0);
        List<ClassDef> nested = model.classes().subList(1, model.classes().size());

        if (asRecord) {
            sb.append(javaRecordHeader(rootDef, model, "", true, jsonProperty));
        } else {
            sb.append("public class ").append(rootDef.name()).append(" {\n");
            sb.append(javaFields(rootDef, model, "    ", jsonProperty));
            sb.append(javaAccessors(rootDef, model, "    "));
        }
        for (ClassDef def : nested) {
            sb.append('\n');
            if (asRecord) {
                sb.append(javaRecordHeader(def, model, "    ", false, jsonProperty));
            } else {
                sb.append("    public static class ").append(def.name()).append(" {\n")
                  .append(javaFields(def, model, "        ", jsonProperty))
                  .append(javaAccessors(def, model, "        "))
                  .append("    }\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** True when any JSON key had to be renamed to become a legal Java field. */
    private boolean hasRenamedField(Model model) {
        for (ClassDef def : model.classes()) {
            for (String key : def.type().fields().keySet()) {
                if (!safeName(Names.camel(key), JAVA_KEYWORDS, "Value").equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String javaRecordHeader(ClassDef def, Model model, String indent,
                                    boolean root, boolean jsonProperty) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("public record ").append(def.name()).append('(');
        List<Map.Entry<String, TypeNode>> fields = new ArrayList<>(def.type().fields().entrySet());
        if (fields.isEmpty()) {
            sb.append(") {\n");
            if (!root) {
                sb.append(indent).append("}\n");
            }
            return sb.toString();
        }
        sb.append('\n');
        for (int i = 0; i < fields.size(); i++) {
            Map.Entry<String, TypeNode> field = fields.get(i);
            String name = safeName(Names.camel(field.getKey()), JAVA_KEYWORDS, "Value");
            sb.append(indent).append("        ");
            if (jsonProperty || !name.equals(field.getKey())) {
                sb.append("@JsonProperty(\"").append(field.getKey()).append("\") ");
            }
            sb.append(javaType(field.getValue(), model)).append(' ').append(name);
            sb.append(i == fields.size() - 1 ? "\n" : ",\n");
        }
        sb.append(indent).append(") {\n");
        if (!root) {
            sb.append(indent).append("}\n");
        }
        return sb.toString();
    }

    private String javaFields(ClassDef def, Model model, String indent, boolean jsonProperty) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
            String name = safeName(Names.camel(field.getKey()), JAVA_KEYWORDS, "Value");
            if (jsonProperty || !name.equals(field.getKey())) {
                sb.append(indent).append("@JsonProperty(\"").append(field.getKey()).append("\")\n");
            }
            sb.append(indent).append("private ").append(javaType(field.getValue(), model))
              .append(' ').append(name).append(";\n");
        }
        return sb.toString();
    }

    private String javaAccessors(ClassDef def, Model model, String indent) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
            String name = safeName(Names.camel(field.getKey()), JAVA_KEYWORDS, "Value");
            String type = javaType(field.getValue(), model);
            String accessor = Names.pascal(name);
            sb.append('\n')
              .append(indent).append("public ").append(type).append(" get").append(accessor).append("() {\n")
              .append(indent).append("    return ").append(name).append(";\n")
              .append(indent).append("}\n\n")
              .append(indent).append("public void set").append(accessor)
              .append('(').append(type).append(' ').append(name).append(") {\n")
              .append(indent).append("    this.").append(name).append(" = ").append(name).append(";\n")
              .append(indent).append("}\n");
        }
        return sb.toString();
    }

    private String javaType(TypeNode node, Model model) {
        return switch (node.kind()) {
            case STRING -> "String";
            case INTEGER -> "Long";
            case NUMBER -> "Double";
            case BOOLEAN -> "Boolean";
            case ARRAY -> "List<" + javaType(elementOf(node), model) + ">";
            case OBJECT -> model.nameOf(node, "Object");
            case NULL, ANY -> "Object";
        };
    }

    // ==================================================================
    // TypeScript
    // ==================================================================

    public String toTypeScript(TypeNode root, String rootName, boolean typeAlias) {
        Model model = requireModel(root, rootName, "interfaces");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.classes().size(); i++) {
            ClassDef def = model.classes().get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("export ").append(typeAlias ? "type " : "interface ").append(def.name())
              .append(typeAlias ? " = {" : " {").append('\n');
            for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
                TypeNode value = field.getValue();
                sb.append("  ").append(tsKey(field.getKey()))
                  .append(value.optional() ? "?: " : ": ")
                  .append(tsType(value, model))
                  .append(value.nullable() ? " | null" : "")
                  .append(";\n");
            }
            sb.append(typeAlias ? "};\n" : "}\n");
        }
        return sb.toString();
    }

    private String tsKey(String key) {
        return Names.isPlainIdentifier(key) ? key : "\"" + key.replace("\"", "\\\"") + "\"";
    }

    private String tsType(TypeNode node, Model model) {
        return switch (node.kind()) {
            case STRING -> "string";
            case INTEGER, NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case ARRAY -> {
                TypeNode element = elementOf(node);
                String inner = tsType(element, model);
                // "string | null" needs parentheses before [] binds to the union.
                yield element.nullable() ? "(" + inner + " | null)[]" : inner + "[]";
            }
            case OBJECT -> model.nameOf(node, "Record<string, unknown>");
            case NULL, ANY -> "any";
        };
    }

    // ==================================================================
    // C#
    // ==================================================================

    public String toCSharp(TypeNode root, String rootName, String namespaceName, boolean jsonProperty) {
        Model model = requireModel(root, rootName, "classes");
        boolean hasNamespace = namespaceName != null && !namespaceName.isBlank();
        String indent = hasNamespace ? "    " : "";

        StringBuilder sb = new StringBuilder();
        sb.append("using System.Collections.Generic;\n");
        if (jsonProperty) {
            sb.append("using System.Text.Json.Serialization;\n");
        }
        sb.append('\n');
        if (hasNamespace) {
            sb.append("namespace ").append(namespaceName.trim()).append("\n{\n");
        }
        for (int i = 0; i < model.classes().size(); i++) {
            ClassDef def = model.classes().get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(indent).append("public class ").append(def.name()).append('\n')
              .append(indent).append("{\n");
            for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
                String name = safeName(Names.pascal(field.getKey()), CSHARP_KEYWORDS, "Value");
                if (jsonProperty) {
                    sb.append(indent).append("    [JsonPropertyName(\"").append(field.getKey()).append("\")]\n");
                }
                sb.append(indent).append("    public ").append(csharpType(field.getValue(), model))
                  .append(' ').append(name).append(" { get; set; }\n");
            }
            sb.append(indent).append("}\n");
        }
        if (hasNamespace) {
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String csharpType(TypeNode node, Model model) {
        String base = switch (node.kind()) {
            case STRING -> "string";
            case INTEGER -> "long";
            case NUMBER -> "double";
            case BOOLEAN -> "bool";
            case ARRAY -> "List<" + csharpType(elementOf(node), model) + ">";
            case OBJECT -> model.nameOf(node, "object");
            case NULL, ANY -> "object";
        };
        // Only value types need the nullable marker; reference types already allow null.
        boolean valueType = switch (node.kind()) {
            case INTEGER, NUMBER, BOOLEAN -> true;
            default -> false;
        };
        return valueType && (node.nullable() || node.optional()) ? base + "?" : base;
    }

    // ==================================================================
    // Python
    // ==================================================================

    public String toPython(TypeNode root, String rootName) {
        Model model = requireModel(root, rootName, "dataclasses");
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n")
          .append("from dataclasses import dataclass\n")
          .append("from typing import Any, List, Optional\n");

        // Leaves first, so a class is always defined before it is referenced.
        List<ClassDef> ordered = new ArrayList<>(model.classes());
        java.util.Collections.reverse(ordered);

        for (ClassDef def : ordered) {
            sb.append("\n\n@dataclass\nclass ").append(def.name()).append(":\n");
            if (def.type().fields().isEmpty()) {
                sb.append("    pass\n");
                continue;
            }
            // Fields with defaults must follow those without.
            List<Map.Entry<String, TypeNode>> required = new ArrayList<>();
            List<Map.Entry<String, TypeNode>> optional = new ArrayList<>();
            for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
                boolean hasDefault = field.getValue().nullable() || field.getValue().optional();
                (hasDefault ? optional : required).add(field);
            }
            for (Map.Entry<String, TypeNode> field : required) {
                sb.append("    ").append(pythonName(field.getKey())).append(": ")
                  .append(pythonType(field.getValue(), model)).append('\n');
            }
            for (Map.Entry<String, TypeNode> field : optional) {
                sb.append("    ").append(pythonName(field.getKey())).append(": Optional[")
                  .append(pythonType(field.getValue(), model)).append("] = None\n");
            }
        }
        return sb.toString();
    }

    private String pythonName(String key) {
        return safeName(Names.snake(key), PYTHON_KEYWORDS, "_value");
    }

    private String pythonType(TypeNode node, Model model) {
        return switch (node.kind()) {
            case STRING -> "str";
            case INTEGER -> "int";
            case NUMBER -> "float";
            case BOOLEAN -> "bool";
            case ARRAY -> "List[" + pythonType(elementOf(node), model) + "]";
            case OBJECT -> model.nameOf(node, "Any");
            case NULL, ANY -> "Any";
        };
    }

    // ==================================================================
    // Go
    // ==================================================================

    public String toGo(TypeNode root, String rootName, String packageName) {
        Model model = requireModel(root, rootName, "structs");
        StringBuilder sb = new StringBuilder();
        sb.append("package ")
          .append(packageName == null || packageName.isBlank() ? "main" : Names.snake(packageName))
          .append('\n');

        for (ClassDef def : model.classes()) {
            sb.append("\ntype ").append(def.name()).append(" struct {\n");
            for (Map.Entry<String, TypeNode> field : def.type().fields().entrySet()) {
                String name = safeName(Names.pascal(field.getKey()), GO_KEYWORDS, "Value");
                sb.append('\t').append(name).append(' ').append(goType(field.getValue(), model))
                  .append(" `json:\"").append(field.getKey());
                if (field.getValue().optional()) {
                    sb.append(",omitempty");
                }
                sb.append("\"`\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String goType(TypeNode node, Model model) {
        return switch (node.kind()) {
            case STRING -> "string";
            case INTEGER -> "int64";
            case NUMBER -> "float64";
            case BOOLEAN -> "bool";
            case ARRAY -> "[]" + goType(elementOf(node), model);
            case OBJECT -> model.nameOf(node, "any");
            case NULL, ANY -> "any";
        };
    }

    private static TypeNode elementOf(TypeNode array) {
        return array.element() == null ? TypeNode.of(Kind.ANY) : array.element();
    }
}
