package com.jsontools.web;

import com.jsontools.model.Tool;
import com.jsontools.model.ToolOptions;
import com.jsontools.model.ToolResult;
import com.jsontools.registry.ToolRegistry;
import com.jsontools.service.RemoteFetchService;
import com.jsontools.service.ToolExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JSON API behind the tool pages. */
@RestController
@RequestMapping("/api")
public class ApiController {

    /** Uploads and pasted payloads are capped well below the multipart limit. */
    private static final int MAX_INPUT_CHARS = 8 * 1024 * 1024;

    private final ToolRegistry registry;
    private final ToolExecutor executor;
    private final RemoteFetchService remote;

    public ApiController(ToolRegistry registry, ToolExecutor executor, RemoteFetchService remote) {
        this.registry = registry;
        this.executor = executor;
        this.remote = remote;
    }

    @PostMapping("/tool/{toolId}")
    public ResponseEntity<ToolResult> run(@PathVariable String toolId, @RequestBody ToolRequest request) {
        Optional<Tool> tool = registry.find(toolId);
        if (tool.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ToolResult.error("Unknown tool: " + toolId));
        }
        if (request.input().length() > MAX_INPUT_CHARS) {
            return ResponseEntity.badRequest()
                    .body(ToolResult.error("Input is larger than the " + (MAX_INPUT_CHARS / (1024 * 1024)) + " MB limit."));
        }
        ToolResult result = executor.execute(toolId, request.input(), request.secondInput(),
                new ToolOptions(request.options()));
        return ResponseEntity.ok(result);
    }

    /** Builds and streams the .xlsx for the JSON to Excel tool. */
    @PostMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestBody ToolRequest request) {
        try {
            byte[] workbook = executor.excelWorkbook(request.input(), new ToolOptions(request.options()));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data.xlsx\"")
                    .body(workbook);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Reads an uploaded file into the input pane. */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The uploaded file is empty."));
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (content.length() > MAX_INPUT_CHARS) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "That file is larger than the "
                                + (MAX_INPUT_CHARS / (1024 * 1024)) + " MB limit."));
            }
            Map<String, String> body = new LinkedHashMap<>();
            body.put("name", file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
            body.put("content", content);
            return ResponseEntity.ok(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read that file: " + e.getMessage()));
        }
    }

    /** Loads a document from a URL into the input pane. */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, String>> fetch(@RequestBody Map<String, String> body) {
        try {
            String content = remote.fetch(body.get("url"));
            return ResponseEntity.ok(Map.of("content", content));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.badRequest().body(Map.of("error", "The request was interrupted."));
        } catch (Exception e) {
            String message = e.getMessage();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message == null ? e.toString() : message));
        }
    }

    /** The tool catalogue, used by the client-side search box. */
    @GetMapping("/tools")
    public List<Map<String, String>> tools() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Tool tool : registry.all()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", tool.getId());
            entry.put("title", tool.getTitle());
            entry.put("tagline", tool.getTagline());
            entry.put("category", tool.getCategory().getLabel());
            entry.put("url", tool.getUrl());
            out.add(entry);
        }
        return out;
    }
}
