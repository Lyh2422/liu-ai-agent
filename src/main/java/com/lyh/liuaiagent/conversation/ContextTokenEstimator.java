package com.lyh.liuaiagent.conversation;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

/**
 * 不依赖具体模型 tokenizer 的保守估算器。中文按一个码点约一个 token，连续 ASCII
 * 按约四个字符一个 token，并为每条消息预留协议开销。
 */
@Component
public class ContextTokenEstimator {
    private static final int MESSAGE_OVERHEAD = 8;

    public int estimate(Message message) {
        return MESSAGE_OVERHEAD + estimate(message == null ? null : message.getText());
    }

    public int estimateTurn(ChatTurn turn) {
        return MESSAGE_OVERHEAD * 2 + estimate(turn.getUserContent()) + estimate(turn.getAssistantContent());
    }

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint <= 0x7f && Character.isLetterOrDigit(codePoint)) {
                asciiRun++;
                continue;
            }
            if (asciiRun > 0) {
                tokens += Math.max(1, (asciiRun + 3) / 4);
                asciiRun = 0;
            }
            if (!Character.isWhitespace(codePoint)) tokens++;
        }
        if (asciiRun > 0) tokens += Math.max(1, (asciiRun + 3) / 4);
        return tokens;
    }

    public String truncate(String text, int maxTokens) {
        if (text == null || maxTokens <= 0) return "";
        if (estimate(text) <= maxTokens) return text;
        if (maxTokens == 1) return "…";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            // 预留一个 token 给截断标记，保证返回值本身不超预算。
            if (estimate(text.substring(0, middle)) <= maxTokens - 1) low = middle;
            else high = middle - 1;
        }
        int safeEnd = low;
        if (safeEnd > 0 && safeEnd < text.length()
                && Character.isHighSurrogate(text.charAt(safeEnd - 1))) safeEnd--;
        return text.substring(0, safeEnd).stripTrailing() + "…";
    }
}
