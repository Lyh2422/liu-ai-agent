package com.lyh.liuaiagent.knowledge;

import com.lyh.liuaiagent.auth.model.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeDocumentStore store;
    private final KnowledgeManagementService management;
    public KnowledgeController(KnowledgeDocumentStore store, KnowledgeManagementService management) {
        this.store = store;
        this.management = management;
    }
    public record Summary(String id, Long version, String title, String filename, int characters, boolean builtin, Long updatedBy, Instant updatedAt) {
        static Summary from(KnowledgeDocument document) {
            return new Summary(document.getId(), document.getVersion(), document.getTitle(), document.getFilename(), document.getContent().length(), document.isBuiltin(), document.getUpdatedBy(), document.getUpdatedAt());
        }
    }
    public record EditRequest(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 120) String title,
                              @NotBlank @Size(max = KnowledgeValidation.MAX_CHARS) String content) {}
    @GetMapping public List<Summary> list() { return store.all().stream().map(Summary::from).toList(); }
    @GetMapping("/{id}") public KnowledgeDocument detail(@PathVariable String id) { return store.get(id); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument upload(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal UserAccount user) throws IOException {
        return management.upload(file, user.getId());
    }
    @PutMapping("/{id}")
    public KnowledgeDocument update(@PathVariable String id, @Valid @RequestBody EditRequest request, @AuthenticationPrincipal UserAccount user) {
        return management.update(id, request.version(), request.title(), request.content(), user.getId());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam long version) { management.delete(id, version); }
}
