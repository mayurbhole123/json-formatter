package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jsontools.model.TypeNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** JSON Schema validation and schema inference. */
@Service
public class SchemaService {

    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String DRAFT_07 = "http://json-schema.org/draft-07/schema#";

    private final JsonService json;

    public SchemaService(JsonService json) {
        this.json = json;
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /** One schema violation, flattened for display. */
    public record Violation(String location, String message) {}

    public List<Violation> validate(JsonNode data, JsonNode schemaNode, String draft) {
        SpecVersion.VersionFlag version = resolveVersion(schemaNode, draft);
        JsonSchema schema = JsonSchemaFactory.getInstance(version).getSchema(schemaNode);

        Set<ValidationMessage> messages = schema.validate(data);
        List<Violation> violations = new ArrayList<>(messages.size());
        for (ValidationMessage message : messages) {
            String location = String.valueOf(message.getInstanceLocation());
            violations.add(new Violation(location.isEmpty() ? "$" : location, message.getMessage()));
        }
        violations.sort((a, b) -> a.location().compareTo(b.location()));
        return violations;
    }

    /** Honours an explicit draft choice, otherwise reads $schema, otherwise 2020-12. */
    private SpecVersion.VersionFlag resolveVersion(JsonNode schemaNode, String draft) {
        if (draft != null && !draft.isBlank() && !"auto".equalsIgnoreCase(draft)) {
            try {
                return SpecVersion.VersionFlag.valueOf(draft);
            } catch (IllegalArgumentException ignored) {
                // Fall through to detection.
            }
        }
        try {
            return SpecVersionDetector.detect(schemaNode);
        } catch (RuntimeException e) {
            return SpecVersion.VersionFlag.V202012;
        }
    }

    // ==================================================================
    // Generation
    // ==================================================================

    public JsonNode generate(TypeNode type, String draft, boolean markRequired) {
        boolean is2020 = !"07".equals(draft);
        ObjectNode root = (ObjectNode) describe(type, markRequired);
        // $schema belongs at the top of the document, so rebuild with it first.
        ObjectNode out = json.mapper().createObjectNode();
        out.put("$schema", is2020 ? DRAFT_2020_12 : DRAFT_07);
        out.setAll(root);
        return out;
    }

    private JsonNode describe(TypeNode type, boolean markRequired) {
        ObjectNode node = json.mapper().createObjectNode();
        switch (type.kind()) {
            case OBJECT -> {
                setType(node, "object", type.nullable());
                ObjectNode properties = node.putObject("properties");
                ArrayNode required = json.mapper().createArrayNode();
                for (Map.Entry<String, TypeNode> field : type.fields().entrySet()) {
                    properties.set(field.getKey(), describe(field.getValue(), markRequired));
                    if (markRequired && !field.getValue().optional()) {
                        required.add(field.getKey());
                    }
                }
                if (!required.isEmpty()) {
                    node.set("required", required);
                }
            }
            case ARRAY -> {
                setType(node, "array", type.nullable());
                TypeNode element = type.element();
                if (element != null && element.kind() != TypeNode.Kind.ANY) {
                    node.set("items", describe(element, markRequired));
                }
            }
            case STRING -> setType(node, "string", type.nullable());
            case INTEGER -> setType(node, "integer", type.nullable());
            case NUMBER -> setType(node, "number", type.nullable());
            case BOOLEAN -> setType(node, "boolean", type.nullable());
            case NULL -> node.put("type", "null");
            case ANY -> {
                // No "type" at all is how JSON Schema spells "anything goes".
            }
        }
        return node;
    }

    /** A nullable value is spelled as the union {@code ["string","null"]}. */
    private void setType(ObjectNode node, String type, boolean nullable) {
        if (nullable) {
            ArrayNode types = node.putArray("type");
            types.add(type);
            types.add("null");
        } else {
            node.put("type", type);
        }
    }
}
