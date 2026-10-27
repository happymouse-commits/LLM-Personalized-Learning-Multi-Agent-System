package com.happymouse.cryd.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 新生引导配置 — 与前端 Questionnaire.vue 的6个问题一一对应
 */
@Component
public class OnboardingConfig {

    public List<StepConfig> getSteps() {
        return STEPS;
    }

    private static final List<StepConfig> STEPS = List.of(
        step("你的 C 语言基础如何？",
            List.of("🌱 完全没接触过", "📖 学过语法，但不会写程序", "💻 能完成基础实验", "🚀 能独立完成中小型程序"),
            "knowledgeLevel",
            Map.of("完全没接触过", 20, "学过语法", 40, "能完成基础实验", 60, "独立完成", 80)),

        step("你希望通过学习 C 语言实现什么目标？",
            List.of("🎓 通过课程考试", "🏆 蓝桥杯/ACM等竞赛", "💼 完成课程设计或项目", "📚 系统学习C语言", "🧑‍💻 为C++/数据结构打基础"),
            "studyMotivation",
            Map.of("通过课程考试", "extrinsic", "蓝桥杯", "extrinsic", "课程设计", "hybrid", "系统学习", "intrinsic", "C++", "intrinsic")),

        step("每天计划学习多久？",
            List.of("⚡ 15分钟以内", "📖 30分钟左右", "📚 1小时左右", "🔥 2小时以上"),
            "learningPace",
            Map.of("15分钟", "slow", "30分钟", "steady", "1小时", "steady", "2小时", "fast")),

        step("你更喜欢哪种学习方式？",
            List.of("📺 视频讲解", "📖 图文教程", "💻 边学边练", "🎯 刷题巩固", "🔄 综合学习（推荐）"),
            "cognitiveStyle",
            Map.of("视频讲解", "visual", "图文教程", "reading", "边学边练", "kinesthetic", "刷题巩固", "kinesthetic", "综合学习", "auditory")),

        step("你更想先学习哪些内容？",
            List.of("基础语法", "顺序、选择、循环", "数组", "函数", "指针", "字符串", "结构体", "文件操作", "动态内存", "综合项目"),
            "interestDirection",
            Map.of("基础语法", "基础语法", "顺序", "控制结构", "数组", "数组", "函数", "函数",
                   "指针", "指针", "字符串", "字符串", "结构体", "结构体", "文件操作", "文件操作",
                   "动态内存", "动态内存", "综合项目", "综合项目")),

        step("你觉得学习 C 语言最大的困难是什么？",
            List.of("😵 看不懂代码", "🤯 指针太难", "📝 不知道怎么做题", "💻 调试困难", "📚 知识点容易忘", "❓ 不知道学到哪了"),
            "weakAreas",
            Map.of("看不懂代码", "基础语法", "指针太难", "指针", "怎么做题", "解题思路",
                   "调试困难", "调试", "容易忘", "知识复习", "学到哪了", "学习规划"))
    );

    private static StepConfig step(String question, List<String> options, String profileKey, Map<String, Object> valueMap) {
        StepConfig s = new StepConfig();
        s.question = question;
        s.options = options;
        s.profileKey = profileKey;
        s.valueMap = valueMap;
        s.diagnostic = false;
        return s;
    }

    public static class StepConfig {
        private String question;
        private List<String> options;
        private String profileKey;
        private Map<String, Object> valueMap;
        private boolean diagnostic;
        private String knowledgePoint;
        private String correctAnswer;
        private String failHint;

        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public String getProfileKey() { return profileKey; }
        public Map<String, Object> getValueMap() { return valueMap; }
        public boolean isDiagnostic() { return diagnostic; }
        public String getKnowledgePoint() { return knowledgePoint; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getFailHint() { return failHint; }
    }
}
