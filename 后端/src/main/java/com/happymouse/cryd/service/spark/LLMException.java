package com.happymouse.cryd.service.spark;

/**
 * LLM 调用异常 — 用于将 API 错误/网络错误从 SparkClient 向上抛出，
 * 让调用方自行决定如何处理，而非被 SparkClient 内部吞掉返回错误字符串。
 */
public class LLMException extends RuntimeException {

    private final int httpCode;

    public LLMException(String message) {
        super(message);
        this.httpCode = 0;
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
        this.httpCode = 0;
    }

    public LLMException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
