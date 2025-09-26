package com.lyh.liuaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PDFGenerationToolTest {

    @Test
    public void testGeneratePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "熏鱼.pdf";
        String content = "熏鱼不熬夜";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
