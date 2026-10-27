package com.happymouse.cryd.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切片器 — 将文档拆分为重叠的文字块供向量检索
 */
@Component
public class DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunker.class);

    private int chunkSize = 512;
    private int chunkOverlap = 64;

    /**
     * 按字符数切片（适合中文，约2字符≈1token）
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        // 按段落优先分割
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() > chunkSize) {
                // 先保存当前块
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    // 保留重叠部分
                    if (current.length() > chunkOverlap) {
                        current = new StringBuilder(current.substring(current.length() - chunkOverlap));
                    } else {
                        current = new StringBuilder();
                    }
                }
                // 如果段落本身超过chunkSize，按句子拆分
                if (para.length() > chunkSize) {
                    List<String> subChunks = splitLongParagraph(para);
                    chunks.addAll(subChunks);
                } else {
                    current.append(para).append("\n");
                }
            } else {
                current.append(para).append("\n");
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        log.debug("文档切片完成: {}个段落 -> {}个chunk", paragraphs.length, chunks.size());
        return chunks;
    }

    private List<String> splitLongParagraph(String text) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("[。！？\\n]");
        StringBuilder current = new StringBuilder();

        for (String sent : sentences) {
            sent = sent.trim();
            if (sent.isEmpty()) continue;

            if (current.length() + sent.length() > chunkSize) {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                }
                current = new StringBuilder(sent).append("。");
            } else {
                current.append(sent).append("。");
            }
        }
        if (current.length() > 0) result.add(current.toString().trim());
        return result;
    }

    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
}
