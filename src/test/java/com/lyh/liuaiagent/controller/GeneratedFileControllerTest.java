package com.lyh.liuaiagent.controller;

import com.lyh.liuaiagent.auth.controller.AuthExceptionHandler;
import com.lyh.liuaiagent.generated.GeneratedFileReference;
import com.lyh.liuaiagent.generated.GeneratedFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeneratedFileControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void downloadsMarkdownAsUtf8AttachmentAndRejectsUnknownIds() throws Exception {
        GeneratedFileStore store = new GeneratedFileStore(tempDir);
        GeneratedFileReference reference = store.saveMarkdown("校园计划.md", "# 校园计划\n\n正文");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GeneratedFileController(store))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();

        mvc.perform(get("/ai/generated-files/{id}", reference.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().string("# 校园计划\n\n正文"));

        mvc.perform(get("/ai/generated-files/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
