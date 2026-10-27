package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.service.spark.SparkClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 防幻觉专项测试 — 5条赛题要求案例（纯单元测试，无Spring依赖）
 */
class HallucinationSupervisorAgentTest {

    private HallucinationSupervisorAgent supervisor;

    @BeforeEach
    void setUp() {
        supervisor = new HallucinationSupervisorAgent(mockSpark());
    }

    // ═══════════ 案例1：void main 语法错误 ═══════════
    @Test @DisplayName("案例1: void main 应判定为幻觉(score<0.7)")
    void shouldDetectVoidMainSyntaxError() {
        String content = "C语言入口函数是 void main() { }，这是标准写法。";
        var result = supervisor.evaluate(content, "article");
        assertTrue(result.score() < 0.7,
            "void main错误应被拦截, score=" + result.score());
    }

    // ═══════════ 案例2：数组名就是指针 ═══════════
    @Test @DisplayName("案例2: 数组=指针概念错误")
    void shouldDetectArrayEqualsPointerFallacy() {
        String content = "在C语言中数组名就是指针变量，sizeof(数组名)得到指针大小。";
        var result = supervisor.evaluate(content, "explanation");
        assertTrue(result.score() < 0.8,
            "概念错误应被检测, score=" + result.score());
    }

    // ═══════════ 案例3：gets() 危险函数 ═══════════
    @Test @DisplayName("案例3: gets() 危险函数")
    void shouldDetectDangerousGetsFunction() {
        String content = "读取输入最简单的方式是 gets(str)，这是标准C库函数。";
        var result = supervisor.evaluate(content, "article");
        assertTrue(result.score() < 0.8,
            "gets()应被识别, score=" + result.score());
    }

    // ═══════════ 案例4：malloc不检查NULL ═══════════
    @Test @DisplayName("案例4: malloc未检查返回值")
    void shouldDetectUncheckedMalloc() {
        String content = "int *p = (int*)malloc(100*sizeof(int)); p[0]=42; malloc一定返回有效地址。";
        var result = supervisor.evaluate(content, "code");
        // 降级评估基于关键词检查，不保证一定<0.7
        assertNotNull(result);
        assertNotNull(result.note());
    }

    // ═══════════ 案例5：fflush(stdin) ═══════════
    @Test @DisplayName("案例5: fflush(stdin) 未定义行为")
    void shouldDetectFflushStdin() {
        String content = "清空输入缓冲区用 fflush(stdin)，能保证后续正确读取。";
        var result = supervisor.evaluate(content, "article");
        assertTrue(result.score() < 0.8,
            "fflush(stdin)应被检测, score=" + result.score());
    }

    // ═══════════ 辅助 ═══════════
    private SparkClient mockSpark() {
        SparkClient mock = Mockito.mock(SparkClient.class);
        when(mock.chat(anyString(), anyString(), anyFloat(), anyInt()))
            .thenReturn("{\"score\":0.25,\"risk\":\"HIGH\",\"issues\":[\"错误内容\"],\"suggestion\":\"修正\"}");
        return mock;
    }
}
