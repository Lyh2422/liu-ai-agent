package com.lyh.liuaiagent.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoveAppKeywordExpansionServiceTest {
    private final LoveAppKeywordExpansionService service = new LoveAppKeywordExpansionService();

    @Test void segmentsDomainWordsAndKeepsIndexTermFrequency() {
        assertEquals(List.of("个人空间", "联系频率", "个人空间"),
                service.tokenize("我的个人空间，联系频率，个人空间"));
        assertEquals(List.of("个人空间", "联系频率"), service.extractKeywords("个人空间联系频率个人空间"));
        assertEquals(List.of("沟通", "沟通", "沟通"), service.tokenize("沟通沟通沟通"));
    }

    @Test void normalizesChineseAndLatinWithTheSameRules() {
        assertEquals(List.of("沟通", "abc123", "隐私"), service.tokenize("沟通ＡＢＣ１２３，隐私！"));
        assertEquals(service.extractKeywords("ＡＢＣ１２３"), service.extractKeywords("abc123"));
        assertTrue(service.extractKeywords("我你了吗？！").isEmpty());
    }

    @Test void retainsUnknownWordsAndTheirRepeatedOccurrences() {
        assertTrue(service.tokenize("星河活动星河").stream().filter("星河"::equals).count() >= 2);
        assertTrue(service.extractKeywords("星河").contains("星河"));
    }

    @Test void expandsColloquialQueryWithoutLosingTheOriginal() {
        String query = "她天天黏着我，一点自己的时间都没有怎么办？";
        var variants = service.expand(query);
        assertEquals(2, variants.size());
        assertEquals(service.normalize(query), variants.getFirst());
        assertTrue(variants.getLast().endsWith("个人空间 边界感 联系频率"));
        assertEquals(variants, new LoveAppKeywordExpansionService().expand(query));
        assertTrue(service.expand("老让我买单").getLast().contains("消费观念 预算 消费"));
        assertTrue(service.expand("翻我手机").getLast().contains("隐私"));
    }

    @Test void doesNotExpandUnrelatedOrEmptyInput() {
        assertEquals(List.of("星河活动"), service.expand("星河活动"));
        assertTrue(service.expand(null).isEmpty());
        assertTrue(service.expand(" ？！ ").isEmpty());
    }

    @Test void limitsAddedTermsEvenForManyMatchedRules() {
        String query = "粘人 冷战 表白 被拒了 不在一个城市 吃醋 翻我手机 光讲道理";
        String normalized = service.normalize(query);
        var variants = service.expand(query);
        assertEquals(2, variants.size());
        assertTrue(variants.getLast().substring(normalized.length() + 1).split(" ").length <= 12);
    }
}
