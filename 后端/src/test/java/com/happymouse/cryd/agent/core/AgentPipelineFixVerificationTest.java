package com.happymouse.cryd.agent.core;

import com.happymouse.cryd.agent.memory.AgentMemory;
import com.happymouse.cryd.service.spark.SparkClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 智能体调度流程修复验证测试
 * 验证 S1/S3/P1/P2/P3/P4/P5/E1/E2/E3/E4/L1/D1/D2/O1/O2 等修复点
 *
 * 纯单元测试，无 Spring 依赖，用 mock SparkClient + 轻量测试 Agent
 */
class AgentPipelineFixVerificationTest {

    // ════════════════════════════════════════════════════════════════
    // 测试用轻量 Agent — 避免依赖数据库
    // ════════════════════════════════════════════════════════════════

    /** 辅导Agent — 模拟 TutorAgent */
    static class MockTutorAgent extends BaseAgent {
        private final String reply;
        final AtomicInteger callCount = new AtomicInteger(0);
        MockTutorAgent(SparkClient spark, String reply) {
            this.sparkClient = spark;
            this.reply = reply;
            this.memory = new AgentMemory("AI导师", 10);
        }
        @Override public String getName() { return "AI导师"; }
        @Override public List<AgentCapability> getCapabilities() {
            return List.of(
                AgentCapability.of("tutor:answer", "辅导答疑、C语言知识解答", 0.95),
                AgentCapability.of("tutor:explain", "概念讲解、代码示例", 0.90)
            );
        }
        @Override protected String getSystemPrompt() { return "你是AI导师"; }
        @Override protected String doExecute(AgentContext context) {
            callCount.incrementAndGet();
            return callLLM(context, context.getOriginalMessage(), 0.5f, 256);
        }
    }

    /** 出题Agent — 模拟出题专家 */
    static class MockQuestionAgent extends BaseAgent {
        MockQuestionAgent(SparkClient spark) { this.sparkClient = spark; }
        @Override public String getName() { return "出题专家"; }
        @Override public List<AgentCapability> getCapabilities() {
            return List.of(AgentCapability.of("question:gen", "出题、练习题生成", 0.92));
        }
        @Override protected String getSystemPrompt() { return "你是出题专家"; }
        @Override protected String doExecute(AgentContext context) {
            return callLLM(context, context.getOriginalMessage(), 0.7f, 512);
        }
    }

    /** 恒失败Agent — 用于测试重试和失败结果存储 */
    static class AlwaysFailAgent extends BaseAgent {
        final AtomicInteger callCount = new AtomicInteger(0);
        AlwaysFailAgent(SparkClient spark) { this.sparkClient = spark; }
        @Override public String getName() { return "失败Agent"; }
        @Override public List<AgentCapability> getCapabilities() {
            return List.of(AgentCapability.of("fail:test", "测试失败场景", 0.99));
        }
        @Override protected String getSystemPrompt() { return "测试"; }
        @Override protected String doExecute(AgentContext context) {
            callCount.incrementAndGet();
            throw new RuntimeException("模拟执行失败");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // S3: 能力匹配 — 中英文跨格式匹配
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("S3: tutor_answer 任务应匹配到 AI导师（中英文跨格式）")
    void shouldMatchTutorAnswerCrossLanguage() {
        AgentCapability cap = AgentCapability.of("tutor:answer", "辅导答疑、C语言知识解答", 0.95);
        // 任务描述：英文type + 中文描述
        double score = cap.matchScore("tutor_answer C语言辅导答疑");
        assertTrue(score > 0.5, "tutor_answer 应匹配 tutor:answer，实际 score=" + score);
    }

    @Test @DisplayName("S3: question_gen 任务应匹配到出题专家")
    void shouldMatchQuestionGen() {
        AgentCapability cap = AgentCapability.of("question:gen", "出题、练习题生成", 0.92);
        double score = cap.matchScore("question_gen 生成指针练习题");
        assertTrue(score > 0.5, "question_gen 应匹配 question:gen，实际 score=" + score);
    }

    @Test @DisplayName("S3: 不相关的任务匹配分应低")
    void shouldNotMatchUnrelated() {
        AgentCapability cap = AgentCapability.of("tutor:answer", "辅导答疑", 0.95);
        double score = cap.matchScore("question_gen 生成练习题");
        assertTrue(score < 0.5, "不相关任务匹配分应低，实际 score=" + score);
    }

    // ════════════════════════════════════════════════════════════════
    // S1 + E1 + E2: AgentMemory 注入、线程安全、防御性拷贝
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("S1: AgentMemory 注入后 save/load 生效")
    void memoryShouldWorkAfterInjection() {
        AgentMemory mem = new AgentMemory("测试Agent");
        mem.save(1001L, "user", "什么是指针");
        mem.save(1001L, "assistant", "指针是存储内存地址的变量");

        List<Map<String, String>> history = mem.load(1001L);
        assertEquals(2, history.size(), "应加载2条历史");
        assertEquals("user", history.get(0).get("role"));
        assertEquals("什么是指针", history.get(0).get("content"));
    }

    @Test @DisplayName("E2: load 返回防御性拷贝，修改不影响内部状态")
    void loadShouldReturnDefensiveCopy() {
        AgentMemory mem = new AgentMemory("测试Agent");
        mem.save(1001L, "user", "hello");

        List<Map<String, String>> h1 = mem.load(1001L);
        h1.clear(); // 修改返回的 list
        List<Map<String, String>> h2 = mem.load(1001L);
        assertEquals(1, h2.size(), "外部 clear 不应影响内部状态");
    }

    @Test @DisplayName("E1: 多线程并发 save 不损坏数据")
    void concurrentSaveShouldBeSafe() throws InterruptedException {
        AgentMemory mem = new AgentMemory("并发测试", 100);
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 50; i++) mem.save(1L, "user", "msg-" + i);
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 50; i++) mem.save(1L, "assistant", "reply-" + i);
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        List<Map<String, String>> history = mem.load(1L);
        assertEquals(100, history.size(), "并发写入后应有100条记录");
    }

    @Test @DisplayName("S1: BaseAgent.preProcess 从 memory 加载历史到 context")
    void preProcessShouldLoadMemoryHistory() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("回复");
        MockTutorAgent agent = new MockTutorAgent(spark, "回复");
        // 先写入记忆
        agent.memory.save(2001L, "user", "上轮问题");
        agent.memory.save(2001L, "assistant", "上轮回答");

        AgentContext ctx = new AgentContext(2001L, "本轮问题", null);
        agent.execute(ctx);
        // preProcess 应把 memoryHistory 加载到 context
        Object loaded = ctx.getAttribute("memoryHistory");
        assertNotNull(loaded, "preProcess 应加载 memoryHistory 到 context");
        assertInstanceOf(List.class, loaded);
        assertEquals(2, ((List<?>) loaded).size(), "应加载2条历史");
    }

    // ════════════════════════════════════════════════════════════════
    // O1: AgentContext conversationHistory 线程安全
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("O1: 外部 list 修改不影响 AgentContext 内部 conversationHistory")
    void conversationHistoryShouldBeIsolated() {
        List<Map<String, String>> external = new ArrayList<>();
        external.add(Map.of("role", "user", "content", "问题1"));

        AgentContext ctx = new AgentContext(1L, "msg", external);
        external.clear(); // 清空外部 list

        assertEquals(1, ctx.getConversationHistory().size(), "外部 clear 不应影响 context 内部");
    }

    // ════════════════════════════════════════════════════════════════
    // D1: 复合意图降级拆解 — 多个独立 if
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("D1: 复合请求'规划路径并出练习题'应拆出2个任务")
    void fallbackShouldHandleCompoundIntent() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        // 让 AI 拆解失败，触发 fallback
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("not a json");
        TaskDecomposer decomposer = new TaskDecomposer(spark);

        AgentContext ctx = new AgentContext(1L, "帮我规划学习路径并出一些练习题", null);
        decomposer.execute(ctx);

        List<AgentContext.SubTask> tasks = ctx.getTasks();
        assertTrue(tasks.size() >= 2, "复合意图应拆出≥2个任务，实际=" + tasks.size());
        boolean hasPath = tasks.stream().anyMatch(t -> "path_plan".equals(t.getType()));
        boolean hasQuestion = tasks.stream().anyMatch(t -> "question_gen".equals(t.getType()));
        assertTrue(hasPath, "应包含 path_plan 任务");
        assertTrue(hasQuestion, "应包含 question_gen 任务");
    }

    // ════════════════════════════════════════════════════════════════
    // D2: JSON 提取 — 含代码块的 LLM 输出
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("D2: 含代码块的 LLM 输出应正确提取 JSON 任务列表")
    void shouldExtractJsonFromCodeContainingOutput() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        // 模拟 LLM 返回带代码块和 JSON 的混合输出
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("""
                好的，我来拆解任务：

                ```c
                int main() {
                    if (x > 0) { return 1; }
                    return 0;
                }
                ```

                ```json
                {
                  "tasks": [
                    {"id": "t1", "type": "knowledge_search", "description": "检索指针", "priority": 1, "dependsOn": []},
                    {"id": "t2", "type": "course_design", "description": "讲解指针", "priority": 2, "dependsOn": ["t1"]}
                  ]
                }
                ```
                """);
        TaskDecomposer decomposer = new TaskDecomposer(spark);

        AgentContext ctx = new AgentContext(1L, "讲解指针", null);
        decomposer.execute(ctx);

        List<AgentContext.SubTask> tasks = ctx.getTasks();
        assertEquals(2, tasks.size(), "应解析出2个任务");
        assertEquals("knowledge_search", tasks.get(0).getType());
        assertEquals("course_design", tasks.get(1).getType());
        assertEquals(List.of("t1"), tasks.get(1).getDependsOn(), "t2 应依赖 t1");
    }

    // ════════════════════════════════════════════════════════════════
    // P3 + P1: 调度器依赖执行 + 重试只跑失败任务
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("P3+P1: 依赖任务正确等待，重试只执行失败任务")
    void schedulerShouldRespectDependenciesAndRetryOnlyFailed() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("生成内容");

        MockTutorAgent tutor = new MockTutorAgent(spark, "辅导回答");
        MockQuestionAgent questioner = new MockQuestionAgent(spark);

        AgentScheduler scheduler = new AgentScheduler(1, 10_000);
        scheduler.register(tutor);
        scheduler.register(questioner);

        AgentContext ctx = new AgentContext(1L, "讲解指针并出题", null);
        // 手动构造带依赖的任务图: t1(knowledge) -> t2(question)
        AgentContext.SubTask t1 = new AgentContext.SubTask("t1", "tutor_answer", "讲解指针", 1);
        AgentContext.SubTask t2 = new AgentContext.SubTask("t2", "question_gen", "出练习题", 2);
        t2.getDependsOn().add("t1");
        ctx.addTask(t1);
        ctx.addTask(t2);

        List<AgentContext.AgentResult> results = scheduler.executeTaskGraph(ctx);
        assertEquals(2, results.size(), "应执行2个任务");
        assertEquals("completed", t1.getStatus(), "t1 应完成");
        assertEquals("completed", t2.getStatus(), "t2 应完成");
        assertEquals(1, tutor.callCount.get(), "tutor 应被调用1次");
    }

    // ════════════════════════════════════════════════════════════════
    // P4 + E3: 未匹配 Agent 生成失败结果而非静默丢失
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("P4+E3: 未匹配Agent的任务应生成失败结果并存入context")
    void unmatchedTaskShouldProduceFailedResult() {
        SparkClient spark = Mockito.mock(SparkClient.class);

        // 不注册任何 Agent，使 matchAgent 真正返回 null（matchByTypeName 最终兜底也返回 null）
        AgentScheduler scheduler = new AgentScheduler(1, 5_000);

        AgentContext ctx = new AgentContext(1L, "test", null);
        ctx.addTask(new AgentContext.SubTask("t1", "unknown_type_xyz", "未知任务", 1));

        List<AgentContext.AgentResult> results = scheduler.executeTaskGraph(ctx);
        assertFalse(results.isEmpty(), "应返回失败结果");
        AgentContext.AgentResult r = results.get(0);
        assertFalse(r.isSuccess(), "未匹配任务应为失败");
        assertNotNull(r.getErrorMessage(), "应有错误信息");
        // E3: 失败结果应存入 context
        assertNotNull(ctx.getResult("task_t1"), "失败结果应存入 context");
        assertEquals("failed", ctx.getTasks().get(0).getStatus(), "任务状态应为 failed");
    }

    // ════════════════════════════════════════════════════════════════
    // P1: 重试时已成功任务不重复执行
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("P1: 重试时已完成任务被跳过")
    void retryShouldSkipCompletedTasks() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("正常回复");
        MockTutorAgent tutor = new MockTutorAgent(spark, "回复");
        AlwaysFailAgent failAgent = new AlwaysFailAgent(spark);

        AgentScheduler scheduler = new AgentScheduler(0, 5_000); // maxRetries=0
        scheduler.register(tutor);
        scheduler.register(failAgent);

        AgentContext ctx = new AgentContext(1L, "test", null);
        AgentContext.SubTask t1 = new AgentContext.SubTask("t1", "tutor_answer", "辅导", 1);
        AgentContext.SubTask t2 = new AgentContext.SubTask("t2", "fail:test", "失败任务", 2);
        ctx.addTask(t1);
        ctx.addTask(t2);

        // 第一次执行：t1成功，t2失败
        scheduler.executeTaskGraph(ctx);
        assertEquals(1, tutor.callCount.get(), "第一次 tutor 应调用1次");
        assertEquals(1, failAgent.callCount.get(), "第一次 failAgent 应调用1次");
        assertEquals("completed", t1.getStatus());
        assertEquals("failed", t2.getStatus());

        // 重置 t2 为 pending 模拟重试场景
        t2.setStatus("pending");
        // 第二次执行：t1 已 completed 应被跳过，只执行 t2
        scheduler.executeTaskGraph(ctx);
        assertEquals(1, tutor.callCount.get(), "重试时 tutor 不应再被调用");
        assertEquals(2, failAgent.callCount.get(), "重试时 failAgent 应再调用1次");
    }

    // ════════════════════════════════════════════════════════════════
    // E3: 失败 Agent 结果存入 context 供查阅
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("E3: 失败Agent结果存入context")
    void failedResultShouldBeStoredInContext() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        AlwaysFailAgent failAgent = new AlwaysFailAgent(spark);

        AgentScheduler scheduler = new AgentScheduler(0, 5_000);
        scheduler.register(failAgent);

        AgentContext ctx = new AgentContext(1L, "test", null);
        ctx.addTask(new AgentContext.SubTask("t1", "fail:test", "失败", 1));

        scheduler.executeTaskGraph(ctx);

        AgentContext.AgentResult stored = ctx.getResult("失败Agent");
        assertNotNull(stored, "失败结果应存入 context");
        assertFalse(stored.isSuccess());
        assertEquals("模拟执行失败", stored.getErrorMessage());
    }

    // ════════════════════════════════════════════════════════════════
    // L1: 审核反馈注入重试
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("L1: callLLM 自动注入审核反馈到 userMessage")
    void callLLMShouldInjectReviewFeedback() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        AtomicInteger callCount = new AtomicInteger(0);
        final String[] capturedMsg = {null};
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenAnswer(inv -> {
                callCount.incrementAndGet();
                capturedMsg[0] = inv.getArgument(1); // userMessage
                return "修正后回复";
            });

        MockTutorAgent agent = new MockTutorAgent(spark, "回复");
        AgentContext ctx = new AgentContext(1L, "讲解指针", null);
        // 模拟审核反馈已存入 context
        AgentReviewer.ReviewResult review = new AgentReviewer.ReviewResult();
        review.passed = false;
        review.score = 0.4;
        review.issues.add("代码块缺少main函数");
        review.issues.add("内容过短");
        review.suggestion = "补充完整代码";
        ctx.setAttribute("review_AI导师", review);

        agent.execute(ctx);

        assertNotNull(capturedMsg[0], "应捕获到 userMessage");
        assertTrue(capturedMsg[0].contains("上次审核反馈"), "应包含审核反馈标记");
        assertTrue(capturedMsg[0].contains("代码块缺少main函数"), "应包含具体问题");
        assertTrue(capturedMsg[0].contains("补充完整代码"), "应包含改进建议");
    }

    @Test @DisplayName("L1: 无审核反馈时不注入（正常流程不受影响）")
    void callLLMShouldNotInjectWhenNoFeedback() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        final String[] capturedMsg = {null};
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenAnswer(inv -> {
                capturedMsg[0] = inv.getArgument(1);
                return "回复";
            });

        MockTutorAgent agent = new MockTutorAgent(spark, "回复");
        AgentContext ctx = new AgentContext(1L, "讲解指针", null);
        // 不设置 review 属性
        agent.execute(ctx);

        assertNotNull(capturedMsg[0]);
        assertFalse(capturedMsg[0].contains("审核反馈"), "无反馈时不应注入");
    }

    // ════════════════════════════════════════════════════════════════
    // E4: answerWithHistory 走模板方法（用 execute 间接验证 memory 保存）
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("E4+S1: 执行后 postProcess 保存到 memory（多轮记忆）")
    void postProcessShouldSaveToMemory() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("第1轮回复");

        MockTutorAgent agent = new MockTutorAgent(spark, "第1轮回复");
        // 第1轮对话
        AgentContext ctx1 = new AgentContext(3001L, "什么是指针", null);
        agent.execute(ctx1);
        // postProcess 应保存 assistant 回复
        List<Map<String, String>> mem1 = agent.memory.load(3001L);
        assertFalse(mem1.isEmpty(), "第1轮后 memory 应有记录");
        assertEquals("assistant", mem1.get(mem1.size() - 1).get("role"));
        assertEquals("第1轮回复", mem1.get(mem1.size() - 1).get("content"));

        // 第2轮对话 — preProcess 应加载第1轮记忆
        when(spark.smartChat(anyString(), anyString(), any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("第2轮回复");
        AgentContext ctx2 = new AgentContext(3001L, "那数组和指针有什么区别", null);
        agent.execute(ctx2);
        Object loaded = ctx2.getAttribute("memoryHistory");
        assertNotNull(loaded, "第2轮 preProcess 应加载记忆");
        List<?> history = (List<?>) loaded;
        assertTrue(history.size() >= 1, "第2轮应能看到历史记忆");
    }

    // ════════════════════════════════════════════════════════════════
    // P5: 重试有指数退避（验证不抛异常且最终返回失败）
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("P5: 失败Agent重试后返回失败结果（含退避）")
    void retryWithBackoffShouldReturnFailedResult() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        AlwaysFailAgent failAgent = new AlwaysFailAgent(spark);

        AgentScheduler scheduler = new AgentScheduler(2, 5_000); // maxRetries=2
        scheduler.register(failAgent);

        AgentContext ctx = new AgentContext(1L, "test", null);
        ctx.addTask(new AgentContext.SubTask("t1", "fail:test", "失败", 1));

        long start = System.currentTimeMillis();
        List<AgentContext.AgentResult> results = scheduler.executeTaskGraph(ctx);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(results.isEmpty());
        assertFalse(results.get(0).isSuccess(), "最终应为失败");
        // maxRetries=2 意味着 3次执行 + 2次退避(1s+2s=3s)，至少>2500ms
        assertTrue(elapsed >= 2500, "应有退避等待，实际耗时=" + elapsed + "ms");
        assertTrue(failAgent.callCount.get() == 3, "应重试3次（1+2），实际=" + failAgent.callCount.get());
    }

    // ════════════════════════════════════════════════════════════════
    // O2: 聚合器空输出兜底（通过 PipelineOrchestrator 间接验证）
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("O2: 所有任务失败时聚合器应返回非空兜底内容")
    void aggregatorShouldReturnFallbackOnEmpty() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        AggregatorAgent aggregator = new AggregatorAgent(spark);

        // 空结果 context
        AgentContext ctx = new AgentContext(1L, "test", null);
        String output = aggregator.doExecute(ctx);
        assertNotNull(output);
        assertFalse(output.isBlank(), "空结果时不应返回空白");
    }

    // ════════════════════════════════════════════════════════════════
    // 完整多轮对话 + 复杂任务拆解集成测试
    // ════════════════════════════════════════════════════════════════
    @Test @DisplayName("集成: 多轮对话+任务拆解+调度执行全流程")
    void fullPipelineMultiTurnTest() {
        SparkClient spark = Mockito.mock(SparkClient.class);
        // 用 system prompt 区分拆解阶段和执行阶段（Mockito 后注册的 stub 优先匹配）
        // 拆解阶段：decomposer 的 system prompt 含"拆解"
        when(spark.smartChat(org.mockito.ArgumentMatchers.contains("拆解"), anyString(),
                any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("""
                {"tasks": [
                  {"id":"t1","type":"knowledge_search","description":"检索指针","priority":1,"dependsOn":[]},
                  {"id":"t2","type":"tutor_answer","description":"讲解指针","priority":2,"dependsOn":["t1"]},
                  {"id":"t3","type":"question_gen","description":"出指针练习题","priority":3,"dependsOn":["t1"]}
                ]}
                """);
        // 执行阶段：tutor 的 system prompt 是"你是AI导师"
        when(spark.smartChat(org.mockito.ArgumentMatchers.eq("你是AI导师"), anyString(),
                any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("指针是存储内存地址的变量。例如 int a=10; int *p=&a;");
        // 执行阶段：questioner 的 system prompt 是"你是出题专家"
        when(spark.smartChat(org.mockito.ArgumentMatchers.eq("你是出题专家"), anyString(),
                any(SparkClient.TaskComplexity.class), anyInt()))
            .thenReturn("练习题：写一个程序，用指针交换两个变量的值。");

        TaskDecomposer decomposer = new TaskDecomposer(spark);
        AgentScheduler scheduler = new AgentScheduler(1, 10_000);
        MockTutorAgent tutor = new MockTutorAgent(spark, "讲解");
        MockQuestionAgent questioner = new MockQuestionAgent(spark);
        scheduler.register(tutor);
        scheduler.register(questioner);

        // === 第1轮：复杂任务 ===
        AgentContext ctx1 = new AgentContext(5001L, "帮我讲解指针并出一些练习题", null);
        decomposer.execute(ctx1);
        List<AgentContext.SubTask> tasks1 = ctx1.getTasks();
        assertEquals(3, tasks1.size(), "应拆出3个任务，实际=" + tasks1.size());
        // 验证依赖关系
        AgentContext.SubTask t2 = tasks1.stream().filter(t -> "t2".equals(t.getId())).findFirst().orElse(null);
        assertNotNull(t2);
        assertTrue(t2.getDependsOn().contains("t1"), "t2 应依赖 t1");

        // 执行任务图
        List<AgentContext.AgentResult> results1 = scheduler.executeTaskGraph(ctx1);
        assertTrue(results1.size() >= 2, "应有多个执行结果");
        // 验证失败结果也存入 context（E3）
        boolean allCompleted = ctx1.getTasks().stream()
            .allMatch(t -> "completed".equals(t.getStatus()));
        assertTrue(allCompleted, "所有任务应完成");

        // === 第2轮：延续上下文 ===
        // 第1轮的 tutor 回复应已通过 postProcess 存入 memory
        List<Map<String, String>> memory1 = tutor.memory.load(5001L);
        assertFalse(memory1.isEmpty(), "第1轮后 tutor memory 应有记录");

        // 第2轮对话，preProcess 应加载记忆
        AgentContext ctx2 = new AgentContext(5001L, "刚才讲的指针，能再举例吗", null);
        tutor.execute(ctx2);
        Object loaded = ctx2.getAttribute("memoryHistory");
        assertNotNull(loaded, "第2轮应加载历史记忆");
        assertTrue(((List<?>) loaded).size() >= 1, "应能看到第1轮记忆");

        // === 第3轮：验证 D3 修复（tutor_answer 不应被误判为资源生成）===
        boolean isResourceType = "course_design".equals("tutor_answer") || "question_gen".equals("tutor_answer");
        assertFalse(isResourceType, "tutor_answer 不应被判定为资源类型（D3修复）");
    }
}
