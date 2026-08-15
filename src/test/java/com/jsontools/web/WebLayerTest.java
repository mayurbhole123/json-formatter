package com.jsontools.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebLayerTest {

    @Autowired
    private MockMvc mvc;

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    @Test
    void homePageListsTheCatalogue() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/WEB-INF/views/index.jsp"))
                .andExpect(model().attributeExists("categories", "toolCount"));
    }

    @Test
    void aToolPageResolvesToTheGenericToolView() throws Exception {
        mvc.perform(get("/json-formatter"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/WEB-INF/views/tool.jsp"))
                .andExpect(model().attributeExists("tool", "sample", "categories"));
    }

    @Test
    void anUnknownPathRendersTheNotFoundPage() throws Exception {
        mvc.perform(get("/no-such-tool"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl("/WEB-INF/views/not-found.jsp"));
    }

    @Test
    void theFormFallbackRunsTheToolServerSide() throws Exception {
        mvc.perform(post("/json-formatter")
                        .param("input", "{\"a\":1}")
                        .param("indent", "2"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/WEB-INF/views/tool.jsp"))
                .andExpect(model().attributeExists("result"));
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    @Test
    void theApiFormatsADocument() throws Exception {
        mvc.perform(post("/api/tool/json-formatter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{\\\"a\\\":1}\",\"options\":{\"indent\":\"2\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.output").value("{\n  \"a\": 1\n}"));
    }

    @Test
    void theApiReportsAParseErrorWithItsPosition() throws Exception {
        mvc.perform(post("/api/tool/json-validator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{oops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.line").value(1));
    }

    @Test
    void theApiRejectsAnUnknownTool() throws Exception {
        mvc.perform(post("/api/tool/not-a-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{}\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("Unknown tool")));
    }

    @Test
    void theCatalogueEndpointListsEveryTool() throws Exception {
        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem("json-to-csv")))
                .andExpect(jsonPath("$[*].id").value(hasItem("json-formatter")));
    }

    @Test
    void theExcelEndpointReturnsAWorkbookAttachment() throws Exception {
        mvc.perform(post("/api/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"[{\\\"a\\\":1}]\",\"options\":{\"sheetName\":\"Data\"}}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("data.xlsx")))
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void theExcelEndpointReportsBadInputAsPlainText() throws Exception {
        mvc.perform(post("/api/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"not json\",\"options\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUploadedFileComesBackAsText() throws Exception {
        mvc.perform(multipart("/api/upload")
                        .file("file", "{\"a\":1}".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("{\"a\":1}"));
    }

    @Test
    void theUrlLoaderRefusesToReachLocalAddresses() throws Exception {
        mvc.perform(post("/api/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:8080/secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("private or local")));
    }

    @Test
    void theUrlLoaderRefusesNonHttpSchemes() throws Exception {
        mvc.perform(post("/api/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"file:///etc/passwd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("http and https")));
    }
}
