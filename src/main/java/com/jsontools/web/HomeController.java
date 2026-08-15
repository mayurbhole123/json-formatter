package com.jsontools.web;

import com.jsontools.model.Tool;
import com.jsontools.model.ToolOptions;
import com.jsontools.model.ToolResult;
import com.jsontools.registry.ToolRegistry;
import com.jsontools.service.SampleService;
import com.jsontools.service.ToolExecutor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renders the JSP pages. The tool page also accepts a plain form POST, so every
 * tool works with JavaScript switched off; the client script upgrades it to
 * in-page updates via the API.
 */
@Controller
public class HomeController {

    private final ToolRegistry registry;
    private final SampleService samples;
    private final ToolExecutor executor;

    public HomeController(ToolRegistry registry, SampleService samples, ToolExecutor executor) {
        this.registry = registry;
        this.samples = samples;
        this.executor = executor;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "JSON Formatter, Validator and Converter");
        model.addAttribute("categories", registry.byCategory());
        model.addAttribute("toolCount", registry.all().size());
        return "index";
    }

    @GetMapping("/{toolId}")
    public String tool(@PathVariable String toolId, Model model, HttpServletResponse response) {
        Optional<Tool> found = registry.find(toolId);
        if (found.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("pageTitle", "Not found");
            model.addAttribute("categories", registry.byCategory());
            model.addAttribute("missingPath", toolId);
            return "not-found";
        }
        Tool tool = found.get();
        addToolAttributes(model, tool);
        model.addAttribute("input", "");
        model.addAttribute("secondInput", "");
        return "tool";
    }

    /** No-JavaScript fallback: run the tool and re-render the page with the result. */
    @PostMapping("/{toolId}")
    public String runTool(@PathVariable String toolId,
                          @RequestParam(name = "input", required = false) String input,
                          @RequestParam(name = "secondInput", required = false) String secondInput,
                          @RequestParam Map<String, String> allParams,
                          Model model,
                          HttpServletResponse response) {
        Optional<Tool> found = registry.find(toolId);
        if (found.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("pageTitle", "Not found");
            model.addAttribute("categories", registry.byCategory());
            model.addAttribute("missingPath", toolId);
            return "not-found";
        }
        Tool tool = found.get();

        Map<String, String> options = new HashMap<>(allParams);
        options.remove("input");
        options.remove("secondInput");
        // An unticked checkbox is simply absent from the POST, so fill in the gaps.
        tool.getOptions().forEach(option -> {
            if (option.isToggle() && !allParams.containsKey(option.getKey())) {
                options.put(option.getKey(), "false");
            }
        });

        ToolResult result = executor.execute(toolId, input, secondInput, new ToolOptions(options));

        addToolAttributes(model, tool);
        model.addAttribute("input", input == null ? "" : input);
        model.addAttribute("secondInput", secondInput == null ? "" : secondInput);
        model.addAttribute("submitted", options);
        model.addAttribute("result", result);
        return "tool";
    }

    private void addToolAttributes(Model model, Tool tool) {
        model.addAttribute("pageTitle", tool.getTitle());
        model.addAttribute("tool", tool);
        model.addAttribute("categories", registry.byCategory());
        model.addAttribute("sample", samples.sampleFor(tool));
        model.addAttribute("secondSample", samples.secondSampleFor(tool));
    }
}
