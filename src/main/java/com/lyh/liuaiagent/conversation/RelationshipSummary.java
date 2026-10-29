package com.lyh.liuaiagent.conversation;

import java.util.*;
import java.util.regex.Pattern;

/** 有来源的抽取式关系笔记：只引用用户陈述，不将助手建议或推测升级为事实。 */
public final class RelationshipSummary {
    private static final List<String> CATEGORIES = List.of("关系背景", "关键事件", "当前困扰", "用户目标", "沟通反馈", "其他陈述");
    private static final Pattern ENTRY = Pattern.compile("^- 【(.+?)】\\[turn=(\\d+)] 用户曾说：(.*)$");
    private record Note(String category, long turn, String quote) {}

    private RelationshipSummary() {}

    public static String update(String previous, List<ChatTurn> olderTurns, int maxChars) {
        List<Note> notes = parse(previous);
        for (ChatTurn turn : olderTurns) {
            // 扫描整段消息的句子，避免遗漏长消息末尾的目标和反馈。
            for (String sentence : turn.getUserContent().split("[。！？!?\\n，,；;]+")) {
                String quote = clean(sentence);
                if (quote.isBlank()) continue;
                String category = classify(quote);
                notes.removeIf(note -> note.category().equals(category) && note.quote().equals(quote));
                notes.add(new Note(category, turn.getId(), quote));
            }
        }
        // 每一类保留最近三条，按轮次标明先后；发生变化时不能把旧状态当作当前状态。
        Collections.reverse(notes);
        List<Note> selected = new ArrayList<>();
        for (String category : CATEGORIES) {
            var matches = notes.stream().filter(note -> note.category().equals(category))
                    .sorted(Comparator.comparingLong(Note::turn).reversed()).limit(3).toList();
            selected.addAll(matches);
        }
        return render(selected, maxChars);
    }

    public static String forContext(String summary, ContextTokenEstimator tokens, int budget) {
        List<Note> notes = parse(summary);
        if (notes.isEmpty() || budget <= 0) return "";
        // 先给各类别分配空间，再补充次新陈述，避免某一类占满全部记忆预算。
        List<Note> ordered = new ArrayList<>();
        for (int rank = 0; rank < 3; rank++) {
            for (String category : CATEGORIES) {
                List<Note> group = notes.stream().filter(n -> n.category().equals(category)).toList();
                if (rank < group.size()) ordered.add(group.get(rank));
            }
        }
        int activeCategories = (int) notes.stream().map(Note::category).distinct().count();
        int perNote = Math.max(1, budget / Math.max(1, activeCategories));
        StringBuilder result = new StringBuilder();
        for (Note note : ordered) {
            String line = tokens.truncate(format(note), perNote);
            if (tokens.estimate(result + line + "\n") > budget) break;
            result.append(line).append('\n');
        }
        return result.toString().strip();
    }

    private static String render(List<Note> notes, int maxChars) {
        if (notes.isEmpty()) return "";
        int perNote = Math.max(1, maxChars / notes.size() - 1);
        StringBuilder result = new StringBuilder();
        for (Note note : notes) {
            String prefix = "- 【" + note.category() + "】[turn=" + note.turn() + "] 用户曾说：";
            int available = Math.min(220, perNote - prefix.length());
            if (available <= 0) continue;
            String line = prefix + clip(note.quote(), available);
            if (result.length() + line.length() + 1 > maxChars) break;
            result.append(line).append('\n');
        }
        return result.toString().strip();
    }

    private static String format(Note note) {
        return "- 【" + note.category() + "】[turn=" + note.turn() + "] 用户曾说：" + note.quote();
    }

    private static List<Note> parse(String text) {
        List<Note> notes = new ArrayList<>();
        if (text == null) return notes;
        for (String line : text.lines().toList()) {
            var match = ENTRY.matcher(line);
            if (match.matches() && CATEGORIES.contains(match.group(1))) {
                notes.add(new Note(match.group(1), Long.parseLong(match.group(2)), match.group(3)));
            } else if (!line.isBlank()) {
                // 兼容旧版摘要；移除旧助手回答，旧记录的来源轮次未知，以 0 表示。
                String user = line.split("；助手：", 2)[0].replaceFirst("^- 用户曾说：", "");
                notes.add(new Note("其他陈述", 0L, clean(user)));
            }
        }
        return notes;
    }

    private static String classify(String text) {
        if (text.matches(".*(试过|尝试|按照|照着|有效|没用|没有用|有用|好转|缓和|更糟|沟通过|道歉后).*")) return "沟通反馈";
        if (text.matches(".*(我想|我希望|我打算|我的目标|不想再|想要|希望能).*")) return "用户目标";
        if (text.matches(".*(难受|担心|害怕|焦虑|困扰|委屈|不安|纠结|怎么办|不回消息).*")) return "当前困扰";
        if (text.matches(".*(吵架|争吵|冷战|分手|复合|表白|告白|失约|昨天|上周|上次|发生).*")) return "关键事件";
        if (text.matches(".*(男朋友|女朋友|对象|伴侣|交往|在一起|异地|认识|恋爱|单身|同班|同学|社团).*")) return "关系背景";
        return "其他陈述";
    }

    private static String clean(String text) {
        return text.replace('<', '＜').replace('>', '＞').replace('\u0000', ' ').replaceAll("\\s+", " ").strip();
    }

    private static String clip(String text, int chars) {
        if (text.length() <= chars) return text;
        int end = Math.max(0, chars - 1);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "…";
    }
}
