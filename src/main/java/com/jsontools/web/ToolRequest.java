package com.jsontools.web;

import java.util.Map;

/**
 * Body of a POST to the tool API.
 *
 * @param input       the main pane's text
 * @param secondInput the right-hand pane's text, for diff / schema tools
 * @param options     the tool's option values, keyed as declared in the registry
 */
public record ToolRequest(String input, String secondInput, Map<String, String> options) {

    public ToolRequest {
        input = input == null ? "" : input;
        secondInput = secondInput == null ? "" : secondInput;
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
