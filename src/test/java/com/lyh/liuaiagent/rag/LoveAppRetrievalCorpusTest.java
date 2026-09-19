package com.lyh.liuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoveAppRetrievalCorpusTest {
    private final LoveAppKeywordExpansionService service = new LoveAppKeywordExpansionService();

    @Test void usesActualChineseFrequencyAndLengthNormalization() {
        var corpus = corpus(doc("repeated", "沟通沟通沟通"), doc("single", "沟通"));
        var results = corpus.bm25Search("沟通", 5);
        assertEquals("repeated", results.getFirst().document().getId());
        // N=2, df=2, tf=3, dl=3, avgdl=2; k1=1.5, b=.75。
        double expected = Math.log(1 + 0.5 / 2.5) * (3 * 2.5) / (3 + 1.5 * (.25 + .75 * 3 / 2));
        assertEquals(expected, results.getFirst().score(), 1e-12);
        assertEquals(results.getFirst().score(), corpus.bm25Search("沟通沟通", 5).getFirst().score(), 1e-12);
    }

    @Test void favorsRareTermsAndPenalizesUnrelatedLength() {
        var corpus = corpus(doc("long", "沟通 礼物 礼物 礼物"), doc("short", "沟通"), doc("rare", "隐私"));
        assertEquals("short", corpus.bm25Search("沟通", 5).getFirst().document().getId());
        assertEquals("rare", corpus.bm25Search("沟通 隐私", 5).getFirst().document().getId());
    }

    @Test void normalizesIndexAndQueryAndHandlesNoMatches() {
        var corpus = corpus(doc("latin", "ＡＢＣ１２３ 沟通"));
        assertEquals("latin", corpus.bm25Search("abc123", 1).getFirst().document().getId());
        assertTrue(corpus.bm25Search(null, 5).isEmpty());
        assertTrue(corpus.bm25Search("星河", 5).isEmpty());
        assertTrue(corpus.bm25Search("沟通", 0).isEmpty());
        assertTrue(corpus.bm25Search("沟通", -1).isEmpty());
        corpus.replaceDocuments(List.of());
        assertTrue(corpus.bm25Search("沟通", 5).isEmpty());
    }

    private LoveAppRetrievalCorpus corpus(Document... documents) {
        var corpus = new LoveAppRetrievalCorpus(null, service);
        corpus.replaceDocuments(List.of(documents));
        return corpus;
    }

    private static Document doc(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }
}
