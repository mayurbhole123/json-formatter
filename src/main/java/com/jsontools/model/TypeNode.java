package com.jsontools.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The inferred shape of a JSON value. Built by {@code TypeInferrer} and consumed
 * by the code generators and the JSON Schema generator, so both agree on what a
 * document looks like.
 */
public final class TypeNode {

    public enum Kind { OBJECT, ARRAY, STRING, INTEGER, NUMBER, BOOLEAN, NULL, ANY }

    private Kind kind;
    private final Map<String, TypeNode> fields = new LinkedHashMap<>();
    private TypeNode element;

    /** The value was null at least once. */
    private boolean nullable;
    /** The key was absent from at least one object merged into the parent. */
    private boolean optional;
    /** Name proposed by the enclosing key, used when emitting a class. */
    private String suggestedName = "Object";

    public TypeNode(Kind kind) {
        this.kind = kind;
    }

    public static TypeNode of(Kind kind) {
        return new TypeNode(kind);
    }

    public Kind kind()                    { return kind; }
    public Map<String, TypeNode> fields() { return fields; }
    public TypeNode element()             { return element; }
    public boolean nullable()             { return nullable; }
    public boolean optional()             { return optional; }
    public String suggestedName()         { return suggestedName; }

    public TypeNode element(TypeNode e)      { this.element = e; return this; }
    public TypeNode nullable(boolean v)      { this.nullable = v; return this; }
    public TypeNode optional(boolean v)      { this.optional = v; return this; }
    public TypeNode suggestedName(String v)  { this.suggestedName = v; return this; }

    public boolean isObject() { return kind == Kind.OBJECT; }
    public boolean isArray()  { return kind == Kind.ARRAY; }

    /**
     * Combines two observations of the same slot - what makes an array of
     * loosely-shaped objects collapse into one class.
     */
    public static TypeNode merge(TypeNode a, TypeNode b) {
        if (a == null) return b;
        if (b == null) return a;

        if (a.kind == Kind.NULL && b.kind != Kind.NULL) return b.copyWith(true, b.optional);
        if (b.kind == Kind.NULL && a.kind != Kind.NULL) return a.copyWith(true, a.optional);

        boolean nullable = a.nullable || b.nullable;
        boolean optional = a.optional || b.optional;

        if (a.kind != b.kind) {
            // int + double is still a number; anything else genuinely conflicts.
            if (isNumeric(a.kind) && isNumeric(b.kind)) {
                return of(Kind.NUMBER).nullable(nullable).optional(optional).suggestedName(a.suggestedName);
            }
            return of(Kind.ANY).nullable(nullable).optional(optional).suggestedName(a.suggestedName);
        }

        switch (a.kind) {
            case OBJECT -> {
                TypeNode merged = of(Kind.OBJECT).nullable(nullable).optional(optional)
                        .suggestedName(a.suggestedName);
                for (Map.Entry<String, TypeNode> e : a.fields.entrySet()) {
                    TypeNode other = b.fields.get(e.getKey());
                    TypeNode field = merge(e.getValue(), other);
                    // Present on the left but missing on the right: not guaranteed.
                    merged.fields.put(e.getKey(), other == null ? field.copyWith(field.nullable, true) : field);
                }
                for (Map.Entry<String, TypeNode> e : b.fields.entrySet()) {
                    if (!merged.fields.containsKey(e.getKey())) {
                        TypeNode field = e.getValue();
                        merged.fields.put(e.getKey(), field.copyWith(field.nullable, true));
                    }
                }
                return merged;
            }
            case ARRAY -> {
                return of(Kind.ARRAY).nullable(nullable).optional(optional)
                        .suggestedName(a.suggestedName)
                        .element(merge(a.element, b.element));
            }
            default -> {
                return of(a.kind).nullable(nullable).optional(optional).suggestedName(a.suggestedName);
            }
        }
    }

    private static boolean isNumeric(Kind k) {
        return k == Kind.INTEGER || k == Kind.NUMBER;
    }

    /** Shallow clone carrying new nullability flags; children are shared (the tree is read-only). */
    private TypeNode copyWith(boolean nullable, boolean optional) {
        TypeNode copy = new TypeNode(this.kind);
        copy.fields.putAll(this.fields);
        copy.element = this.element;
        copy.nullable = nullable;
        copy.optional = optional;
        copy.suggestedName = this.suggestedName;
        return copy;
    }
}
