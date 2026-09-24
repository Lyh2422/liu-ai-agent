package com.lyh.liuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 有可靠资料时按资料回答；没有资料时保留用户问题，并切换到有边界的普通情感支持。
 */
final class LoveAppContextualQueryAugmenter implements QueryAugmenter {

    static final String EMPTY_CONTEXT_MARKER = "本次没有检索到能可靠支持回答的校园知识库资料";

    private static final String GROUNDED_PROMPT = """
            下面是经过相关性筛选的校园情感知识库资料：

            ---------------------
            {context}
            ---------------------

            请结合资料回答用户问题。涉及学校制度、校内服务、联系方式、开放时间等可核验事实时，
            只能使用资料中明确出现的内容；资料没有覆盖的事实不要补写。一般性的情绪支持和沟通建议
            可以结合常识，但不要把它说成学校官方结论。自然作答，不要使用“根据知识库”“根据上下文”之类的机械表述。

            用户问题：{query}
            """;

    private static final String EMPTY_CONTEXT_PROMPT = """
            %s。
            请继续回应用户，但遵守这些边界：
            1. 如果用户在倾诉情绪或询问关系相处，可以提供温和、具体的一般性建议，不必机械拒答，也不要假装引用了校内资料。
            2. 如果用户询问学校制度、校内机构、联系方式、费用、开放时间等具体事实，坦诚说明暂时没有查到相关资料，并建议通过学校官方渠道核实；不要编造。
            3. 如果出现自伤、他伤、暴力、胁迫或人身安全风险，优先建议联系可信任的人、学校相关老师或当地紧急援助。
            4. 不要向用户解释检索流程或使用“知识库未命中”这样的技术术语。

            用户原问题：%s
            """;

    private final PromptTemplate groundedPrompt = new PromptTemplate(GROUNDED_PROMPT);

    @Override
    public Query augment(Query query, List<Document> documents) {
        if (!documents.isEmpty()) {
            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            return query.mutate()
                    .text(groundedPrompt.render(Map.of("context", context, "query", query.text())))
                    .build();
        }
        return query.mutate()
                .text(EMPTY_CONTEXT_PROMPT.formatted(EMPTY_CONTEXT_MARKER, query.text()))
                .build();
    }
}
