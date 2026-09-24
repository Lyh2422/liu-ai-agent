package com.lyh.liuaiagent.conversation;

import com.lyh.liuaiagent.auth.model.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** 用户可以查看并删除系统保存的结构化长期事实。 */
@RestController
@RequestMapping("/ai/memory/facts")
public class UserMemoryController {
    private final ConversationMemoryManager memory;

    public UserMemoryController(ConversationMemoryManager memory) {
        this.memory = memory;
    }

    public record FactView(String id, String type, String value, double confidence, Instant updatedAt) {
        static FactView from(UserMemoryFact fact) {
            return new FactView(fact.getId(), fact.getFactType(), fact.getFactValue(),
                    fact.getConfidence(), fact.getUpdatedAt());
        }
    }

    @GetMapping
    public List<FactView> list(@AuthenticationPrincipal UserAccount user) {
        return memory.listFacts(user.getId()).stream().map(FactView::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserAccount user, @PathVariable String id) {
        memory.deleteFact(user.getId(), id);
    }
}
