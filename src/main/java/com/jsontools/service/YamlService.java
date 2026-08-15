package com.jsontools.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** YAML read / write / validate, backed by Jackson's YAML dataformat. */
@Service
public class YamlService {

    private final ObjectMapper yaml;

    public YamlService() {
        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE);
        this.yaml = new ObjectMapper(factory);
    }

    public JsonNode read(String text) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("No input provided. Paste or upload some YAML first.");
        }
        JsonNode node = yaml.readTree(text);
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("Input is empty or not valid YAML.");
        }
        return node;
    }

    public String write(JsonNode node) throws IOException {
        return yaml.writeValueAsString(node).trim();
    }

    /** Round-trips through the parser, which normalises indentation and quoting. */
    public String format(String text) throws IOException {
        return write(read(text));
    }

    public static class Validation {
        private final boolean valid;
        private final String message;

        Validation(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    public Validation validate(String text) {
        try {
            read(text);
            return new Validation(true, "Valid YAML");
        } catch (Exception e) {
            String msg = e.getMessage();
            return new Validation(false, msg == null ? e.toString() : msg);
        }
    }
}
