package com.happymouse.cryd.service.agent;

import com.happymouse.cryd.model.entity.LearningResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 视频资源智能体 — 根据学生画像推荐 Bilibili C语言教学视频
 * 课程来源：浙江大学翁恺C语言程序设计（BV1dr4y1n7vA，共100集）
 * 不做全量导入，而是根据薄弱点、知识水平智能筛选
 */
@Component
public class VideoAgent implements ResourceAgent {

    private static final Logger log = LoggerFactory.getLogger(VideoAgent.class);

    /** Bilibili 视频基础链接 — 浙大翁恺C语言（BV1dr4y1n7vA） */
    private static final String COURSE_BASE = "https://www.bilibili.com/video/BV1dr4y1n7vA/?p=";

    /**
     * 浙江大学翁恺 C语言程序设计 — 100集完整课程库
     * {标题, 集数p, 知识点, 难度, 章节, 前置知识水平要求}
     */
    private static final Object[][] COURSE_LIBRARY = {
        // === 第1周：C语言入门 ===
        {"C语言简史", 1, "C语言概述", "easy", "ch1", 0},
        {"计算机与编程语言：计算机怎么做事情的", 2, "编程语言", "easy", "ch1", 0},
        {"计算机的思维方式：重复是计算机最擅长的", 3, "编程思维", "easy", "ch1", 0},
        {"为什么是C：C语言在工业界的地位", 4, "C语言概述", "easy", "ch1", 0},
        {"C语言和版本的简单历史", 5, "C语言历史", "easy", "ch1", 0},
        {"编程软件：推荐DevC++", 6, "开发环境", "easy", "ch1", 0},
        {"第一个C程序：编辑、编译和运行", 7, "Hello World", "easy", "ch1", 0},
        {"详解第一个程序：程序框架、printf", 8, "main函数", "easy", "ch1", 0},
        {"做点计算：输出算术结果", 9, "算术运算", "easy", "ch1", 0},
        {"MacOSX命令行编译运行C程序", 10, "开发环境", "easy", "ch1", 0},
        // === 第2周：变量与运算 ===
        {"第二周概述", 11, "章节概览", "easy", "ch2", 10},
        {"变量定义：如何定义一个变量", 12, "变量", "easy", "ch2", 10},
        {"变量赋值与初始化：读入数字", 13, "变量赋值", "easy", "ch2", 15},
        {"变量输入：scanf读入用户输入", 14, "scanf", "easy", "ch2", 15},
        {"常量vs变量：不变的量是常量", 15, "常量", "easy", "ch2", 15},
        {"浮点数：整数运算与浮点数的区别", 16, "浮点数", "medium", "ch2", 20},
        {"表达式：运算符和算子、取余计算", 17, "表达式", "easy", "ch2", 20},
        {"运算符优先级：结合关系、赋值运算符", 18, "运算符优先级", "medium", "ch2", 25},
        {"交换变量：如何交换两个变量的值", 19, "变量交换", "medium", "ch2", 25},
        {"复合赋值和递增递减运算符", 20, "复合赋值", "medium", "ch2", 25},
        {"如何使用PAT系统做编程练习题", 21, "PAT练习", "easy", "ch2", 25},
        // === 第3周：判断 ===
        {"PAT再解释", 22, "PAT练习", "easy", "ch3", 25},
        {"第二周习题解析", 23, "习题解析", "easy", "ch3", 25},
        {"0的故事", 24, "数值处理", "easy", "ch3", 25},
        {"做判断：if语句根据条件决定做不做", 25, "if语句", "easy", "ch3", 30},
        {"判断的条件：关系运算、比较运算符", 26, "关系运算", "easy", "ch3", 30},
        {"找零计算器：判断、注释、流程图", 27, "if应用", "medium", "ch3", 35},
        {"否则的话：if-else", 28, "if-else", "easy", "ch3", 35},
        {"if语句再探：省略大括号的情况", 29, "if语句", "medium", "ch3", 35},
        {"嵌套的if-else", 30, "嵌套if", "medium", "ch3", 40},
        {"级联的if-else", 31, "级联if", "medium", "ch3", 40},
        {"多路分支：switch-case语句", 32, "switch", "medium", "ch3", 40},
        // === 第4周：循环（while） ===
        {"循环：有些事情就得用循环解决", 33, "循环概念", "easy", "ch4", 40},
        {"while循环：条件满足就不断做", 34, "while循环", "easy", "ch4", 40},
        {"do-while循环：先做再判断", 35, "do-while", "easy", "ch4", 40},
        {"猜数游戏", 36, "循环应用", "medium", "ch4", 45},
        {"算平均数", 37, "循环应用", "medium", "ch4", 45},
        {"整数求逆", 38, "循环应用", "medium", "ch4", 45},
        // === 第5周：for循环 ===
        {"for循环：最古老的循环", 39, "for循环", "easy", "ch5", 45},
        {"循环的计算和选择：如何选择不同的循环", 40, "循环选择", "medium", "ch5", 45},
        {"循环控制：break和continue", 41, "break/continue", "medium", "ch5", 50},
        {"嵌套的循环", 42, "嵌套循环", "medium", "ch5", 50},
        {"从嵌套的循环中跳出", 43, "break嵌套", "hard", "ch5", 55},
        {"前n项求和", 44, "求和", "medium", "ch5", 50},
        {"整数分解", 45, "整数分解", "medium", "ch5", 50},
        {"求最大公约数", 46, "最大公约数", "hard", "ch5", 55},
        // === 第6周：数据类型 ===
        {"编程练习解析：给定条件的整数集", 47, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：水仙花数", 48, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：九九乘法表", 49, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：统计素数求和", 50, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：猜数游戏", 51, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：n项求和", 52, "PAT练习", "medium", "ch6", 50},
        {"编程练习解析：综合练习", 53, "PAT练习", "medium", "ch6", 50},
        {"数据类型：C语言有哪些基础数据类型", 54, "数据类型", "easy", "ch6", 50},
        {"整数类型：除了int还有多少整数类型", 55, "整数类型", "medium", "ch6", 55},
        {"整数的内部表达：负数如何表达", 56, "补码", "hard", "ch6", 60},
        {"整数的范围：越界了会怎样", 57, "整数范围", "hard", "ch6", 60},
        {"整数的格式化：8进制和16进制", 58, "格式化输出", "medium", "ch6", 55},
        {"选择整数类型：没什么特殊需要就用int", 59, "类型选择", "easy", "ch6", 55},
        {"浮点类型：double和float", 60, "浮点类型", "medium", "ch6", 55},
        {"浮点的范围与精度", 61, "浮点精度", "hard", "ch6", 60},
        {"字符类型", 62, "char类型", "medium", "ch6", 55},
        {"逃逸字符", 63, "转义字符", "medium", "ch6", 55},
        {"类型转换：如何在不同类型之间做转换", 64, "类型转换", "medium", "ch6", 55},
        {"逻辑类型：关系运算和逻辑运算结果", 65, "逻辑类型", "easy", "ch6", 55},
        {"逻辑运算：与、或、非运算", 66, "逻辑运算", "medium", "ch6", 55},
        {"条件运算和逗号运算", 67, "条件运算", "medium", "ch6", 55},
        // === 第7周：函数 ===
        {"初见函数", 68, "函数概念", "easy", "ch7", 55},
        {"函数的定义和使用", 69, "函数定义", "easy", "ch7", 55},
        {"从函数中返回", 70, "函数返回值", "medium", "ch7", 55},
        {"函数原型", 71, "函数原型", "medium", "ch7", 60},
        {"参数传递", 72, "函数参数", "medium", "ch7", 60},
        {"本地变量", 73, "局部变量", "medium", "ch7", 60},
        {"函数庶事：main()是什么", 74, "函数细节", "medium", "ch7", 60},
        // === 第8周：数组 ===
        {"初试数组", 75, "数组概念", "easy", "ch7", 60},
        {"定义数组", 76, "数组定义", "medium", "ch7", 60},
        {"数组的例子：统计个数", 77, "数组应用", "medium", "ch7", 60},
        {"数组运算", 78, "数组运算", "medium", "ch7", 60},
        {"数组例子：素数", 79, "素数算法", "hard", "ch7", 65},
        {"二维数组", 80, "二维数组", "medium", "ch7", 60},
        // === 第9周：指针 ===
        {"取地址运算：&运算符取得变量的地址", 81, "取地址", "medium", "ch8", 60},
        {"指针：指针变量就是记录地址的变量", 82, "指针概念", "hard", "ch8", 65},
        {"指针的使用：指针有什么用", 83, "指针使用", "hard", "ch8", 65},
        {"指针与数组：为什么sizeof不对了", 84, "指针与数组", "hard", "ch8", 65},
        {"指针与const：指针本身和所指变量都可能const", 85, "指针const", "hard", "ch8", 65},
        {"指针运算", 86, "指针运算", "hard", "ch8", 70},
        {"动态内存分配", 87, "malloc", "hard", "ch8", 70},
        // === 第10周：字符串 ===
        {"字符串", 88, "字符串概念", "medium", "ch8", 60},
        {"字符串变量", 89, "字符串变量", "medium", "ch8", 60},
        {"字符串输入输出", 90, "字符串IO", "medium", "ch8", 60},
        {"字符串数组", 91, "字符串数组", "medium", "ch8", 60},
        {"单字符输入输出", 92, "字符IO", "medium", "ch8", 60},
        {"字符串函数strlen", 93, "strlen", "medium", "ch8", 60},
        {"字符串函数strcat", 94, "strcat", "medium", "ch8", 60},
        {"字符串函数strcpy", 95, "strcpy", "medium", "ch8", 60},
        {"字符串搜索函数", 96, "字符串搜索", "medium", "ch8", 60},
        // === 第11周 + 附录 ===
        {"枚举", 97, "枚举类型", "medium", "ch8", 60},
        {"ACLLib介绍：几个小游戏演示", 98, "图形库", "easy", "ch9", 0},
        {"Win32API简单介绍", 99, "API", "easy", "ch9", 0},
        {"DevC++建ACLLib项目：第一个窗口", 100, "图形编程", "easy", "ch9", 0},
    };

    @Override
    public String getResourceType() { return ResourceType.COURSE_VIDEO.getId(); }

    @Override
    public boolean validateJsonFormat(String aiReply) {
        // 视频资源返回URL，不要求JSON格式
        return aiReply != null && !aiReply.isBlank() && aiReply.length() > 5;
    }

    /**
     * 视频资源自检
     */
    @Override
    public double selfCheck(LearningResource resource) {
        double score = 1.0;
        String content = resource.getContent();
        if (content == null) return 0.0;

        // 必须包含B站链接
        if (!content.contains("bilibili.com")) score -= 0.3;

        // 必须有标题
        if (resource.getTitle() == null || resource.getTitle().isEmpty()) score -= 0.2;

        // 内容长度（应该是URL，较短）
        if (content.length() < 10) score -= 0.1;

        return Math.max(0, score);
    }

    /**
     * 判断该Agent是否适用于当前知识点
     * 视频资源适用于所有C语言知识点
     */
    @Override
    public boolean supports(String chapterName, String knowledgePoint, String weakAreas) {
        // 视频资源适用于所有C语言学习
        return true;
    }

    /**
     * 生成视频资源推荐
     * 根据学生画像（知识水平、薄弱环节）推荐最合适的B站课程视频
     *
     * @param chapterName     章节名称
     * @param knowledgePoint  知识点
     * @param knowledgeLevel  知识水平（0-100）
     * @param weakAreas       薄弱环节（逗号分隔）
     * @return 推荐的视频资源列表（返回第一个最匹配的）
     */
    @Override
    public LearningResource generate(String chapterName, String knowledgePoint,
                                     String knowledgeLevel, String weakAreas) {
        int level = 30;
        try {
            level = Integer.parseInt(knowledgeLevel);
        } catch (NumberFormatException ignored) {}

        // 解析薄弱环节关键词
        Set<String> weakKeywords = new LinkedHashSet<>();
        if (weakAreas != null && !weakAreas.isBlank()) {
            for (String part : weakAreas.split("[,，、]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) weakKeywords.add(trimmed);
            }
        }

        // 计算每节课的推荐分
        List<ScoredVideo> scored = new ArrayList<>();
        for (Object[] row : COURSE_LIBRARY) {
            String title = (String) row[0];
            int episode = (int) row[1];
            String kp = (String) row[2];
            String diff = (String) row[3];
            int requiredLevel = (int) row[5];

            // 跳过难度远超学生水平的（差距 > 50分）
            if (requiredLevel > level + 50) continue;

            int score = 0;
            // 薄弱点匹配 +20（高权重）
            for (String wk : weakKeywords) {
                if (kp.contains(wk) || wk.contains(kp)) {
                    score += 20;
                    break;
                }
            }
            // 知识点精确匹配 +25（最高权重）
            if (knowledgePoint != null) {
                if (kp.equals(knowledgePoint)) {
                    score += 25; // 精确匹配
                } else if (kp.contains(knowledgePoint) || knowledgePoint.contains(kp)) {
                    score += 15; // 部分匹配
                }
            }
            // 难度合适 +5
            if ("easy".equals(diff) && level < 40) score += 5;
            else if ("medium".equals(diff) && level >= 30 && level < 70) score += 5;
            else if ("hard".equals(diff) && level >= 60) score += 5;

            scored.add(new ScoredVideo(title, episode, kp, diff, score));
        }

        // 按分数降序排列，取前1个
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        if (scored.isEmpty()) {
            log.warn("没有找到合适的视频资源: knowledgePoint={}, level={}", knowledgePoint, level);
            return null;
        }

        ScoredVideo best = scored.get(0);

        // 构建资源实体
        LearningResource video = new LearningResource();
        video.setTitle("📺 " + best.title);
        video.setType(ResourceType.COURSE_VIDEO.getId());
        video.setContent(COURSE_BASE + best.episode);
        video.setKnowledgePoint(best.kp);
        video.setDifficulty(best.diff);
        video.setGeneratedBy("video-agent");
        video.setCategory("翁恺C语言");

        log.info("视频资源推荐: knowledgePoint={}, level={}, weak={}, recommended=P{} {}",
                knowledgePoint, level, weakKeywords, best.episode, best.title);

        return video;
    }

    /**
     * 评分视频内部类
     */
    private record ScoredVideo(String title, int episode, String kp, String diff, int score) {}
}
