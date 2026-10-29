package com.lyh.liuaiagent.conversation;

import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.regex.Pattern;

/** 当前会话的原始历史检索；不依赖 embedding 服务，也不跨账号或混合不同关系。 */
final class ConversationHistoryRecall {
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9]{2,}");
    private static final Set<String> STOP = Set.of("怎么", "如何", "上次", "之前", "记得", "那个", "这个", "我们", "你们", "他们", "什么", "继续", "帮我", "可以", "一下");

    private ConversationHistoryRecall() {}

    static String recall(ChatTurnRepository turns, Conversation conversation, long through,
                         String query, ContextTokenEstimator tokens, int budget) {
        if (through <= 0 || budget <= 0) return "";
        List<String> keywords = keywords(query);
        Map<Long, ChatTurn> candidates = new HashMap<>();
        // 每个查询词使用数据库过滤后限量，旧事件不会因候选只含最近 N 轮而完全不可见。
        for (String keyword : keywords) {
            for (ChatTurn turn : turns.searchOlderUserContent(conversation.getId(), conversation.getUserId(), through,
                    keyword, PageRequest.of(0, 12))) candidates.put(turn.getId(), turn);
        }
        List<ChatTurn> ranked = candidates.values().stream()
                .sorted(Comparator.<ChatTurn>comparingInt(turn -> score(turn.getUserContent(), keywords)).reversed()
                        .thenComparing(ChatTurn::getId, Comparator.reverseOrder())).limit(3).toList();
        StringBuilder result = new StringBuilder();
        for (ChatTurn turn : ranked) {
            String prefix = "- [turn=" + turn.getId() + "] 用户原话：";
            int remaining = budget - tokens.estimate(result.toString()) - tokens.estimate(prefix);
            if (remaining < 16) break;
            String quote = excerpt(turn.getUserContent(), keywords);
            result.append(prefix).append(tokens.truncate(quote, Math.min(180, remaining))).append('\n');
        }
        return result.toString().strip();
    }

    static List<String> keywords(String text) {
        var matcher = WORDS.matcher(text.toLowerCase(Locale.ROOT));
        LinkedHashSet<String> words = new LinkedHashSet<>();
        // 优先保留明确的领域词，避免长问题中的无关片段耗尽查询次数。
        for (String domain : List.of("吵架", "冷战", "异地", "分手", "复合", "表白", "道歉", "约会", "毕业", "礼物", "沟通", "失约")) {
            if (text.contains(domain)) words.add(domain);
        }
        while (matcher.find() && words.size() < 8) {
            String word = matcher.group();
            if (word.matches("[a-z0-9]+")) words.add(word);
            else {
                int[] points = word.codePoints().toArray();
                for (int i = 0; i + 1 < points.length && words.size() < 8; i++) {
                    String pair = new String(points, i, 2);
                    if (!STOP.contains(pair)) words.add(pair);
                }
            }
        }
        return words.stream().limit(8).toList();
    }

    private static int score(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        return (int) keywords.stream().filter(lower::contains).count();
    }

    private static String excerpt(String text, List<String> keywords) {
        String clean = text.replace('<', '＜').replace('>', '＞').replaceAll("\\s+", " ").strip();
        String lower = clean.toLowerCase(Locale.ROOT);
        int match = keywords.stream().mapToInt(lower::indexOf).filter(index -> index >= 0).min().orElse(0);
        int start = Math.max(0, match - 40);
        if (start > 0 && Character.isLowSurrogate(clean.charAt(start))) start--;
        return (start > 0 ? "…" : "") + clean.substring(start);
    }
}
