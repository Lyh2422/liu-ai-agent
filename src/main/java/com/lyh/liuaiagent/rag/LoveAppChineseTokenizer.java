package com.lyh.liuaiagent.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 轻量领域词典正向最大匹配；未登录词使用 2～4 字片段兜底。 */
final class LoveAppChineseTokenizer {
    private static final Pattern SEGMENTS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们",
            "的", "了", "吗", "呢", "吧", "啊", "呀", "怎么办", "怎么", "如何", "请问");
    private static final String DOMAIN_WORDS = "恋爱 校园 大学 对方 对象 恋人 朋友 沟通 道歉 修复 "
            + "学习 约会 消费 预算 礼物 见面 计划 时间 联系 情绪 倾诉 安慰 尊重 同意 拒绝 "
            + "隐私 手机 密码 聊天 记录 公开 恋情 亲密 边界 独处 空间 频率 "
            + "毕业 未来 城市 规划 考研 工作 实习 家长 家庭 信任 欺骗 安全 "
            + "复合 前任 失恋 分手 表白 告白 喜欢 好感 尴尬 邀请 冷战 吵架 暧昧 吃醋 异地";
    private final Set<String> dictionary;
    private final int longestWord;

    LoveAppChineseTokenizer(Collection<String> expansionVocabulary) {
        dictionary = new HashSet<>(List.of(DOMAIN_WORDS.split(" ")));
        dictionary.addAll(STOP_WORDS);
        dictionary.addAll(expansionVocabulary);
        longestWord = dictionary.stream().mapToInt(word -> word.codePointCount(0, word.length())).max().orElse(1);
    }

    static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").strip();
    }

    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = SEGMENTS.matcher(normalize(text));
        while (matcher.find()) {
            String segment = matcher.group();
            if (segment.matches("[a-z0-9]+")) {
                tokens.add(segment);
                continue;
            }
            int[] characters = segment.codePoints().toArray();
            int offset = 0;
            while (offset < characters.length) {
                String word = longestMatch(characters, offset);
                if (word != null) {
                    if (!STOP_WORDS.contains(word)) tokens.add(word);
                    offset += word.codePointCount(0, word.length());
                } else {
                    int start = offset++;
                    while (offset < characters.length && longestMatch(characters, offset) == null) offset++;
                    addUnknownTokens(characters, start, offset, tokens);
                }
            }
        }
        return tokens;
    }

    private String longestMatch(int[] characters, int offset) {
        for (int length = Math.min(longestWord, characters.length - offset); length > 0; length--) {
            String word = new String(characters, offset, length);
            if (dictionary.contains(word)) return word;
        }
        return null;
    }

    private void addUnknownTokens(int[] characters, int start, int end, List<String> tokens) {
        if (end - start == 1) {
            tokens.add(new String(characters, start, 1));
            return;
        }
        for (int length = 2; length <= Math.min(4, end - start); length++) {
            for (int offset = start; offset + length <= end; offset++) {
                tokens.add(new String(characters, offset, length));
            }
        }
    }
}
