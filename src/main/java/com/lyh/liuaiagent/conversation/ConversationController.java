package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.auth.model.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ai/conversations")
public class ConversationController {
    private final ConversationStore store;
    public ConversationController(ConversationStore store) { this.store = store; }
    public record CreateRequest(@NotNull Conversation.AppType appType) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create(@AuthenticationPrincipal UserAccount user, @Valid @RequestBody CreateRequest request) {
        return store.create(user.getId(), request.appType());
    }

    @GetMapping
    public List<Conversation> list(@AuthenticationPrincipal UserAccount user, @RequestParam Conversation.AppType appType) {
        return store.list(user.getId(), appType);
    }

    @GetMapping("/{id}")
    public ConversationStore.Detail detail(@AuthenticationPrincipal UserAccount user, @PathVariable String id) {
        return store.detail(user.getId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserAccount user, @PathVariable String id) {
        store.delete(user.getId(), id);
    }
}
