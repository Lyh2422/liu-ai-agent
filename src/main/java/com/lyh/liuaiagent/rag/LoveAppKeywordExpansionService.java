package com.lyh.liuaiagent.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

@Component
public class LoveAppKeywordExpansionService {

    private static final int MAX_EXPANSION_TERMS = 12;
    private final List<ExpansionRule> rules;
    private final LoveAppChineseTokenizer tokenizer;

    public LoveAppKeywordExpansionService() {
        // 文件顺序就是规则优先级，避免无序 Map 使查询结果不稳定。
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("rag/love-query-expansions.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            rules = reader.lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(ExpansionRule::parse).toList();
        } catch (IOException e) {
            throw new IllegalStateException("无法加载恋爱知识库关键词扩展规则", e);
        }
        tokenizer = new LoveAppChineseTokenizer(rules.stream()
                .flatMap(rule -> Stream.concat(rule.aliases().stream(), rule.terms().stream())).toList());
    }

    public List<String> expand(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) return List.of();
        var extraTerms = new LinkedHashSet<String>();
        for (ExpansionRule rule : rules) {
            if (rule.aliases().stream().anyMatch(normalized::contains)) {
                rule.terms().stream().filter(term -> !normalized.contains(term)).forEach(extraTerms::add);
            }
        }
        if (extraTerms.isEmpty()) return List.of(normalized);
        String expansion = String.join(" ", extraTerms.stream().limit(MAX_EXPANSION_TERMS).toList());
        // 保留自然语言原句，仅增加一个领域扩展版本，不把大量字符碎片送给向量模型。
        return List.of(normalized, normalized + " " + expansion);
    }

    public List<String> extractKeywords(String text) {
        return List.copyOf(new LinkedHashSet<>(tokenize(text)));
    }

    /** 文档索引必须保留重复词，BM25 才能计算真实词频。 */
    public List<String> tokenize(String text) {
        return tokenizer.tokenize(text);
    }

    public String normalize(String text) {
        return LoveAppChineseTokenizer.normalize(text);
    }

    private record ExpansionRule(List<String> aliases, List<String> terms) {
        static ExpansionRule parse(String line) {
            String[] columns = line.split("\t", -1);
            if (columns.length != 2) throw new IllegalStateException("关键词扩展规则必须为两列：" + line);
            return new ExpansionRule(words(columns[0]), words(columns[1]));
        }

        private static List<String> words(String column) {
            List<String> words = Arrays.stream(column.split("\\|"))
                    .map(LoveAppChineseTokenizer::normalize).filter(word -> !word.isBlank()).distinct().toList();
            if (words.isEmpty()) throw new IllegalStateException("关键词扩展规则不能为空");
            return words;
        }
    }
}
