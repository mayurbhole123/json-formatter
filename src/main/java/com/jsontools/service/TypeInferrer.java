package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jsontools.model.TypeNode;
import com.jsontools.model.TypeNode.Kind;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Derives a {@link TypeNode} tree from a sample document. Every element of an
 * array is merged, so a class generated from a 500-item array reflects all 500
 * items rather than only the first.
 */
@Service
public class TypeInferrer {

    public TypeNode infer(JsonNode node, String nameHint) {
        if (node == null || node.isNull()) {
            return TypeNode.of(Kind.NULL).nullable(true).suggestedName(nameHint);
        }
        if (node.isObject()) {
            TypeNode type = TypeNode.of(Kind.OBJECT).suggestedName(nameHint);
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                type.fields().put(entry.getKey(), infer(entry.getValue(), entry.getKey()));
            }
            return type;
        }
        if (node.isArray()) {
            TypeNode element = null;
            String elementHint = Names.singular(nameHint);
            for (JsonNode child : node) {
                element = TypeNode.merge(element, infer(child, elementHint));
            }
            if (element == null) {
                element = TypeNode.of(Kind.ANY).suggestedName(elementHint);
            }
            return TypeNode.of(Kind.ARRAY).suggestedName(nameHint).element(element);
        }
        if (node.isTextual())  return TypeNode.of(Kind.STRING).suggestedName(nameHint);
        if (node.isBoolean())  return TypeNode.of(Kind.BOOLEAN).suggestedName(nameHint);
        if (node.isIntegralNumber()) return TypeNode.of(Kind.INTEGER).suggestedName(nameHint);
        if (node.isNumber())   return TypeNode.of(Kind.NUMBER).suggestedName(nameHint);
        return TypeNode.of(Kind.ANY).suggestedName(nameHint);
    }
}
