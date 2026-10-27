package com.happymouse.cryd.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.happymouse.cryd.model.entity.*;
import com.happymouse.cryd.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据初始化 - 启动时创建默认账号和C语言题库
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserRepository sysUserRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigRepository systemConfigRepository;
    private final LearningResourceRepository resourceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StudentRepository studentRepository;

    public DataInitializer(SysUserRepository sysUserRepository,
                           CourseRepository courseRepository,
                           ChapterRepository chapterRepository,
                           QuestionRepository questionRepository,
                           PasswordEncoder passwordEncoder,
                           SystemConfigRepository systemConfigRepository,
                           LearningResourceRepository resourceRepository,
                           JdbcTemplate jdbcTemplate,
                           StudentRepository studentRepository) {
        this.sysUserRepository = sysUserRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.questionRepository = questionRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigRepository = systemConfigRepository;
        this.resourceRepository = resourceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.studentRepository = studentRepository;
    }

    @Override
    public void run(String... args) {
        initDefaultUsers();
        initCProgrammingBank();
        initSystemConfig();
        syncSequences();
        initDemoResources();
        initStudent1FullSeed();
    }

    /**
     * IDENTITY → SEQUENCE 迁移后，修正所有序列起点到当前最大ID之后
     * 仅在PostgreSQL上执行（MySQL使用自增ID，无需同步序列）
     */
    private void syncSequences() {
        try {
            // 检查是否为MySQL数据库
            String dbType = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);
            if (dbType != null && dbType.toLowerCase().contains("mysql")) {
                log.info("MySQL数据库，跳过序列同步");
                return;
            }

            List<String> sequences = jdbcTemplate.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name LIKE '%_seq'", String.class);
            for (String seq : sequences) {
                String tbl = seq.replace("_seq", "");
                try {
                    Integer maxId = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(id), 0) FROM " + tbl, Integer.class);
                    if (maxId != null && maxId > 0) {
                        jdbcTemplate.execute("SELECT setval('" + seq + "', " + maxId + ")");
                        log.info("序列同步: {}({})", seq, maxId);
                    }
                } catch (Exception ignored) {
                    // 序列名和表名不对应，跳过
                }
            }
        } catch (Exception e) {
            log.warn("序列同步失败: {}", e.getMessage());
        }
    }

    /**
     * 初始化系统配置默认值
     */
    private void initSystemConfig() {
        if (systemConfigRepository.count() > 0) return;

        List<SystemConfig> defaults = List.of(
            createConfig("llm.apiUrl", "https://api.deepseek.com/v1/chat/completions", "DeepSeek API 地址", "llm"),
            createConfig("llm.model", "deepseek-chat", "默认模型：DeepSeek-V4-Pro", "llm"),
            createConfig("llm.temperature", "0.5", "默认 Temperature", "llm"),
            createConfig("llm.maxTokens", "4096", "默认 Max Tokens", "llm"),
            createConfig("llm.topP", "0.9", "默认 Top-P", "llm"),
            createConfig("llm.timeout", "60", "请求超时（秒）", "llm")
        );

        systemConfigRepository.saveAll(defaults);
        log.info("系统配置已初始化: DeepSeek-V4-Pro 为默认模型");
    }

    private SystemConfig createConfig(String key, String value, String desc, String category) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(desc);
        config.setCategory(category);
        return config;
    }

    private void initDefaultUsers() {
        List<DefaultUser> defaults = List.of(
            new DefaultUser("admin1", "123456", "系统管理员", "admin", "", "信息中心", "13800000001", null),
            new DefaultUser("student1", "123456", "张同学", "student", "计科2301", "", "13800000002", "2023010001")
        );

        for (DefaultUser d : defaults) {
            if (sysUserRepository.findByUsername(d.username).isEmpty()) {
                SysUser user = new SysUser();
                user.setUsername(d.username);
                user.setPassword(passwordEncoder.encode(d.password));
                user.setNickname(d.nickname);
                user.setRole(d.role);
                user.setClassName(d.className);
                user.setDepartment(d.department);
                user.setPhone(d.phone);
                user.setStudentId(d.studentId);
                user.setStatus("active");
                sysUserRepository.save(user);
            }
        }
    }

    /**
     * 初始化谭浩强《C语言程序设计》题库
     */
    private void initCProgrammingBank() {
        try {
            ClassPathResource resource = new ClassPathResource("question-bank/c-programming.json");
            String jsonStr;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                jsonStr = sb.toString();
            }

            JSONObject root = JSON.parseObject(jsonStr);
            JSONArray chapters = root.getJSONArray("chapters");
            if (chapters.isEmpty()) return;

            // 获取或创建课程
            List<Course> existingCourses = courseRepository.findAllByName("C语言程序设计");
            Course course = existingCourses.isEmpty() ? null : existingCourses.get(0);
            if (course == null) {
                course = new Course();
                course.setName("C语言程序设计");
                course.setCode("C001");
                course.setSemester("2025-2026-2");
                course.setClassName("计科2301"); // 设置授课班级
                course.setDescription("谭浩强《C语言程序设计》课后习题题库，每小节含4道选择题和1道代码编写题");
                course = courseRepository.save(course);
                log.info("创建课程：C语言程序设计（班级：计科2301）");
            } else if (course.getClassName() == null || course.getClassName().isEmpty()) {
                // 如果课程已存在但没有设置班级，补充设置
                course.setClassName("计科2301");
                course = courseRepository.save(course);
                log.info("更新课程班级：计科2301");
            }

            // 创建或更新章节（检查是否已存在，避免重复）
            int createdCount = 0;
            int updatedCount = 0;
            for (int i = 0; i < chapters.size(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                String chapterNameRaw = ch.getString("chapterName");
                if (chapterNameRaw == null) chapterNameRaw = ch.getString("name"); // 兼容两种格式
                final String chapterName = chapterNameRaw;
                JSONArray sections = ch.getJSONArray("sections");
                JSONArray questionsRaw = ch.getJSONArray("questions"); // 有些格式直接在章节级别
                if (questionsRaw == null && sections != null) {
                    // 从 sections 里汇总所有 questions
                    questionsRaw = new JSONArray();
                    for (int s = 0; s < sections.size(); s++) {
                        JSONArray sq = sections.getJSONObject(s).getJSONArray("questions");
                        if (sq != null) {
                            for (int q = 0; q < sq.size(); q++) {
                                questionsRaw.add(sq.get(q));
                            }
                        }
                    }
                }
                final JSONArray questions = questionsRaw != null ? questionsRaw : new JSONArray();

                // 查找是否已有同名章节
                List<Chapter> existing = chapterRepository.findByStatusOrderByOrderNum("published");
                Chapter chapter = existing.stream()
                        .filter(c -> c.getName().equals(chapterName))
                        .findFirst().orElse(null);

                if (chapter == null) {
                    chapter = new Chapter();
                    chapter.setCourseId(course.getId());
                    chapter.setName(chapterName);
                    chapter.setDescription(ch.getString("description") != null ? ch.getString("description") : chapterName);
                    Integer orderNum = ch.getInteger("orderNum");
                    if (orderNum == null) orderNum = ch.getInteger("chapterId"); // 兼容两种格式
                    chapter.setOrderNum(orderNum != null ? orderNum : i + 1);
                    chapter.setStatus("published");
                    chapter.setPublishedAt(LocalDateTime.now());
                    chapter.setQuestions(questions.toJSONString());
                    chapterRepository.save(chapter);
                    createdCount++;
                } else if (chapter.getQuestions() == null || getQuestionCount(chapter.getQuestions()) < questions.size()) {
                    // 更新已有章节的题目（JSON文件有更多题目时）
                    chapter.setQuestions(questions.toJSONString());
                    chapterRepository.save(chapter);
                    updatedCount++;
                }
            }

            log.info("C语言题库初始化完成: 新增{}章, 更新{}章, 共{}章", createdCount, updatedCount, chapters.size());

            // 逐题导入题库表 (question)
            int importedQ = 0;
            // 循环外一次性加载已有题目，构建去重索引，避免每轮 findAll() 全表扫描
            java.util.Set<String> existingKeys = new java.util.HashSet<>();
            for (Question existing : questionRepository.findAll()) {
                String ec = existing.getContent();
                String ech = existing.getChapterName();
                if (ec != null && ech != null) existingKeys.add(ec + "\0" + ech);
            }
            for (int i = 0; i < chapters.size(); i++) {
                JSONObject ch = chapters.getJSONObject(i);
                String chapterNameQ = ch.getString("chapterName") != null ? ch.getString("chapterName") : ch.getString("name");
                Integer chapterOrderRaw = ch.getInteger("orderNum");
                if (chapterOrderRaw == null) chapterOrderRaw = ch.getInteger("chapterId");
                int chapterOrder = chapterOrderRaw != null ? chapterOrderRaw : i + 1;

                // 汇总sections里的questions
                JSONArray sectionsQ = ch.getJSONArray("sections");
                JSONArray questionsQ = ch.getJSONArray("questions");
                if (questionsQ == null && sectionsQ != null) {
                    questionsQ = new JSONArray();
                    for (int s = 0; s < sectionsQ.size(); s++) {
                        JSONArray sq = sectionsQ.getJSONObject(s).getJSONArray("questions");
                        if (sq != null) {
                            for (int qi = 0; qi < sq.size(); qi++) {
                                questionsQ.add(sq.get(qi));
                            }
                        }
                    }
                }
                if (questionsQ == null) continue;

                final String chName = chapterNameQ;
                for (int j = 0; j < questionsQ.size(); j++) {
                    JSONObject q = questionsQ.getJSONObject(j);
                    String type = q.getString("type");
                    String content = q.getString("question") != null ? q.getString("question") : q.getString("content");

                    // 检查是否已存在（按内容去重）— 用 Set O(1) 查询替代 findAll().stream()
                    if (content != null && existingKeys.contains(content + "\0" + chName)) continue;

                    Question question = new Question();
                    question.setType(type);
                    question.setContent(content);
                    if ("choice".equals(type) && q.containsKey("options")) {
                        question.setOptions(q.getJSONArray("options").toJSONString());
                    }
                    question.setAnswer(q.getString("answer"));
                    question.setKnowledgePoint(q.getString("knowledgePoint"));
                    question.setAnalysis(q.getString("explanation") != null ? q.getString("explanation") : q.getString("analysis"));
                    question.setDifficulty(q.getString("difficulty") != null ? q.getString("difficulty") : "medium");
                    question.setChapterName(chName);
                    question.setChapterOrder(chapterOrder);
                    question.setCourseId(course.getId());
                    question.setSource("imported");
                    questionRepository.save(question);
                    importedQ++;
                    // 新增题目同步加入索引，防止同批次重复导入
                    if (content != null) existingKeys.add(content + "\0" + chName);
                }
            }
            log.info("题库表导入完成: {} 道题目", importedQ);

            log.info("题库JSON路径: question-bank/c-programming.json");
            log.info("如需扩展题目, 启动后端后调用: POST /api/teacher/expand-question-bank");
        } catch (Exception e) {
            log.error("C语言题库初始化失败：{}", e.getMessage(), e);
        }
    }

    private int getQuestionCount(String questionsJson) {
        try { return JSON.parseArray(questionsJson).size(); } catch (Exception e) { return 0; }
    }

    /**
     * student1 演示账号 — 预置全类型资源展示排版
     */
    private void initDemoResources() {
        try {
            var userOpt = sysUserRepository.findByUsername("student1");
            if (userOpt.isEmpty()) return;
            Long sysUserId = userOpt.get().getId();
            // 通过 sysUserId 查找 Student 实体
            var studentOpt = studentRepository.findBySysUserId(sysUserId);
            Long studentId = studentOpt.map(Student::getId).orElse(sysUserId);

            // 已有演示资源则跳过
            if (resourceRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                    .anyMatch(r -> "demo".equals(r.getGeneratedBy()))) {
                return;
            }

            log.info("初始化 student1 演示资源...");

            // 演示文章
            LearningResource article = new LearningResource();
            article.setTitle("📄 C语言入门：从Hello World开始");
            article.setType("article");
            article.setContent("## 你的第一个C程序\n\n```c\n#include <stdio.h>\nint main() {\n    printf(\"Hello, World!\\n\");\n    return 0;\n}\n```\n\n### 代码解析\n- `#include <stdio.h>` 包含标准输入输出头文件\n- `int main()` 是程序的入口函数\n- `printf()` 用于输出文字到屏幕\n- `return 0` 表示程序正常结束\n\n### 编译运行\n在终端中输入 `gcc hello.c -o hello && ./hello` 即可看到输出。");
            article.setKnowledgePoint("C语言概述");
            article.setDifficulty("easy");
            article.setStudentId(studentId);
            article.setGeneratedBy("demo");
            article.setCategory("演示·文章");
            article.setTags("HelloWorld·main函数·入门");
            article.setShared(false);

            LearningResource summary = new LearningResource();
            summary.setTitle("📋 变量与数据类型学习总结");
            summary.setType("summary");
            summary.setContent("## 变量与数据类型学习总结\n\n### 核心要点回顾\n1. **变量声明**：必须先声明后使用，变量名不能以数字开头\n2. **基本类型**：int(4字节)、float(4字节)、double(8字节)、char(1字节)\n3. **类型转换**：整数除法结果为整数，赋值给float时自动转为浮点数\n4. **sizeof运算符**：获取数据类型或变量占用的字节数\n\n### 学习建议\n- 多动手写代码验证类型大小\n- 注意整数除法的陷阱\n- 理解隐式类型转换的规则\n- 下一步学习：数组与字符串");
            summary.setKnowledgePoint("变量");
            summary.setDifficulty("easy");
            summary.setStudentId(studentId);
            summary.setGeneratedBy("demo");
            summary.setCategory("演示·总结");
            summary.setTags("变量·数据类型·类型转换·总结");
            summary.setShared(false);

            LearningResource weakness = new LearningResource();
            weakness.setTitle("🎯 C语言薄弱环节分析");
            weakness.setType("weakness");
            weakness.setContent("## 薄弱环节诊断\n\n| 知识点 | 当前水平 | 目标 | 差距 |\n|--------|---------|------|------|\n| 指针 | 30/100 | 70 | 🔴 大 |\n| 内存管理 | 25/100 | 65 | 🔴 大 |\n| 结构体 | 45/100 | 70 | 🟡 中 |\n| 文件操作 | 20/100 | 55 | 🟡 中 |\n\n### 建议学习路径\n1. 先巩固指针基础（1周）—— 重点理解 & 和 * 运算符\n2. 结合数组练习指针运算（3天）—— 理解指针与数组的关系\n3. 学习动态内存分配（5天）—— 掌握 malloc/free\n4. 用结构体+指针实现链表（1周）—— 综合练习");
            weakness.setKnowledgePoint("C语言基础");
            weakness.setDifficulty("medium");
            weakness.setStudentId(studentId);
            weakness.setGeneratedBy("demo");
            weakness.setCategory("演示·分析");
            weakness.setTags("薄弱点·诊断·建议");
            weakness.setShared(false);

            LearningResource courseVideo = new LearningResource();
            courseVideo.setTitle("📺 浙大MOOC：C语言指针与数组");
            courseVideo.setType("course-video");
            courseVideo.setContent("https://www.icourse163.org/learn/ZJU-9001?tid=9001#/learn/content?type=detail&id=1209511001");
            courseVideo.setKnowledgePoint("指针");
            courseVideo.setDifficulty("hard");
            courseVideo.setStudentId(studentId);
            courseVideo.setGeneratedBy("course-video");
            courseVideo.setCategory("浙大MOOC");
            courseVideo.setTags("浙大·MOOC·视频·指针");
            courseVideo.setShared(true);

            LearningResource coursePpt = new LearningResource();
            coursePpt.setTitle("📑 浙大MOOC：C语言课程课件");
            coursePpt.setType("course-ppt");
            coursePpt.setContent("浙大C语言MOOC课程课件——翁恺教授主讲\n\n> 来源于中国大学MOOC平台浙江大学《C语言程序设计》\n\n### 课件内容\n- C语言基础知识体系\n- 配套视频教学\n- 课后习题与讨论\n\n📺 视频链接：https://www.icourse163.org/learn/ZJU-9001");
            coursePpt.setKnowledgePoint("C语言概述");
            coursePpt.setDifficulty("easy");
            coursePpt.setStudentId(studentId);
            coursePpt.setGeneratedBy("course-ppt");
            coursePpt.setCategory("浙大MOOC·课件");
            coursePpt.setTags("浙大·MOOC·PPT·课件");
            coursePpt.setShared(true);

            LearningResource explanation = new LearningResource();
            explanation.setTitle("📖 指针深度讲解");
            explanation.setType("explanation");
            explanation.setContent("## 什么是指针\n\n指针是C语言中最强大的特性之一。简单来说，**指针是一个存储内存地址的变量**。\n\n### 指针的定义\n```c\nint a = 10;\nint *p = &a;  // p存储了a的地址\n```\n\n| 概念 | 符号 | 说明 |\n|------|------|------|\n| 取地址 | `&` | 获取变量的内存地址 |\n| 解引用 | `*` | 通过地址访问变量值 |\n| 指针变量 | `int *p` | 声明一个指向int的指针 |\n\n### 指针与数组\n数组名本身就是指向首元素的指针：\n```c\nint arr[] = {1, 2, 3};\nint *p = arr;      // 等价于 &arr[0]\n*(p + 1) === arr[1]; // true!\n```\n\n### 常见错误\n- **野指针**：未初始化的指针\n- **空指针**：指向NULL的指针\n- **悬垂指针**：指向已释放内存的指针");
            explanation.setKnowledgePoint("指针");
            explanation.setDifficulty("hard");
            explanation.setStudentId(studentId);
            explanation.setGeneratedBy("demo");
            explanation.setCategory("演示·讲解");
            explanation.setTags("指针·地址·解引用·数组·内存");
            explanation.setShared(false);

            LearningResource mindmap = new LearningResource();
            mindmap.setTitle("🗺️ C语言知识体系思维导图");
            mindmap.setType("mindmap");
            mindmap.setContent("## C语言核心知识体系\n\n- 基础语法\n  - 变量与常量\n  - 数据类型 (int/float/char/double)\n  - 运算符与表达式\n  - 输入输出 (printf/scanf)\n- 控制结构\n  - 选择结构 (if-else/switch)\n  - 循环结构 (for/while/do-while)\n  - 跳转语句 (break/continue/goto)\n- 数组与字符串\n  - 一维数组\n  - 二维数组\n  - 字符数组与字符串\n  - 字符串处理函数\n- 函数\n  - 函数定义与调用\n  - 参数传递\n  - 递归\n  - 作用域\n- 指针\n  - 指针基础\n  - 指针与数组\n  - 指针与函数\n  - 多级指针\n- 结构体\n  - struct定义\n  - typedef\n  - union共用体\n  - enum枚举\n- 文件操作\n  - fopen/fclose\n  - 读写操作\n  - 文件定位");
            mindmap.setKnowledgePoint("C语言知识体系");
            mindmap.setDifficulty("medium");
            mindmap.setStudentId(studentId);
            mindmap.setGeneratedBy("demo");
            mindmap.setCategory("演示·导图");
            mindmap.setTags("知识体系·思维导图·总览");
            mindmap.setShared(false);

            LearningResource code = new LearningResource();
            code.setTitle("💻 完整示例：学生成绩管理系统");
            code.setType("code");
            code.setContent("## 学生成绩管理系统（结构体+数组+函数）\n\n```c\n#include <stdio.h>\n#include <string.h>\n\n#define MAX_STUDENTS 50\n\ntypedef struct {\n    char name[50];\n    int score;\n    char grade;\n} Student;\n\nvoid calculateGrade(Student *s) {\n    if (s->score >= 90) s->grade = 'A';\n    else if (s->score >= 80) s->grade = 'B';\n    else if (s->score >= 70) s->grade = 'C';\n    else if (s->score >= 60) s->grade = 'D';\n    else s->grade = 'F';\n}\n\nint main() {\n    Student students[MAX_STUDENTS];\n    int count = 0;\n    \n    printf(\"输入学生姓名和成绩(输入done结束)：\\n\");\n    while (count < MAX_STUDENTS) {\n        printf(\"学生%d: \", count + 1);\n        if (scanf(\"%s %d\", students[count].name, &students[count].score) != 2)\n            break;\n        calculateGrade(&students[count]);\n        count++;\n    }\n    \n    printf(\"\\n成绩汇总：\\n\");\n    for (int i = 0; i < count; i++) {\n        printf(\"%s: %d分, 等级%c\\n\",\n               students[i].name, students[i].score, students[i].grade);\n    }\n    return 0;\n}\n```");
            code.setKnowledgePoint("结构体");
            code.setDifficulty("hard");
            code.setStudentId(studentId);
            code.setGeneratedBy("demo");
            code.setCategory("演示·代码");
            code.setTags("结构体·数组·函数·指针·综合示例");
            code.setShared(false);

            resourceRepository.saveAll(List.of(article, summary, weakness, explanation, mindmap, code, courseVideo, coursePpt));
            log.info("student1 演示资源已初始化: 8篇覆盖全类型");

        } catch (Exception e) {
            log.warn("演示资源初始化失败: {}", e.getMessage());
        }
    }

    /**
     * student1 完整种子数据 — 画像 + AI生成资源(含幻觉评分) + 课程推荐资源 + 打卡记录
     */
    private void initStudent1FullSeed() {
        try {
            var userOpt = sysUserRepository.findByUsername("student1");
            if (userOpt.isEmpty()) return;
            Long sysUserId = userOpt.get().getId();

            // === 1. 创建或更新 Student 画像 ===
            var existingStudent = studentRepository.findBySysUserId(sysUserId);
            Student student;
            if (existingStudent.isPresent()) {
                student = existingStudent.get();
                log.info("student1 画像已存在，更新画像数据");
            } else {
                student = new Student();
                student.setUsername("student_" + sysUserId);
                student.setSysUserId(sysUserId);
            }
            // 写入演示画像数据（无论新建还是已有）
            student.setNickname("张同学");
            student.setKnowledgeLevel(55);
            student.setCognitiveStyle("visual");
            student.setLearningPreference("exercise");
            student.setLearningPace("steady");
            student.setInterestDirection("嵌入式开发,游戏开发,算法");
            student.setWeakAreas("指针,内存管理,结构体,文件操作");
            student.setStudyMotivation("intrinsic");
            student.setFocusLevel("medium");
            student.setProgress(40);
            student.setTotalStudyMinutes(320);
            student.setStreakDays(5);
            student = studentRepository.save(student);
            log.info("student1 画像: level={}, weakAreas={}", student.getKnowledgeLevel(), student.getWeakAreas());
            Long studentId = student.getId();

            // === 2. 检查是否已有种子资源 ===
            long seedCount = resourceRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                    .filter(r -> "seed".equals(r.getCategory())).count();
            if (seedCount > 0) {
                log.info("student1 种子资源已存在({}篇)，跳过", seedCount);
                return;
            }

            log.info("初始化 student1 完整种子数据...");

            // === 3. 智能体生成资源（8篇，各种类型 + 幻觉评分） ===
            List<LearningResource> agentResources = List.of(
                buildResource(studentId, "📄 C语言发展史与标准演进", "article",
                    "## C语言的诞生\n\nC语言由**Dennis Ritchie**于1972年在贝尔实验室开发。\n\n### 重要标准\n| 年份 | 标准 | 别称 |\n|------|------|------|\n| 1989 | ANSI C | C89 |\n| 1990 | ISO C | C90 |\n| 1999 | C99 | 现代C |\n| 2011 | C11 | 现行主流 |\n| 2018 | C17 | 最新修订 |\n\n> C语言至今仍是操作系统、嵌入式开发的核心语言。",
                    "C语言概述", "easy", "画像分析师", "seed", "C语言,历史,标准", 0.95, "LOW", "内容基于公开标准文档，事实准确"),

                buildResource(studentId, "✏️ 指针与数组专项练习", "article",
                    "### 练习1：指针运算\n```c\nint arr[] = {10, 20, 30, 40, 50};\nint *p = arr + 1;\nprintf(\"%d\\n\", *(p + 2));\n```\n**输出：** 40\n**解析：** p指向arr[1]=20，p+2指向arr[3]=40。\n\n### 练习2：指针与函数\n```c\nvoid swap(int *a, int *b) {\n    int t = *a; *a = *b; *b = t;\n}\n```\n**作用：** 通过指针交换两个变量的值。",
                    "指针", "medium", "出题专家", "seed", "指针,数组,函数,练习", 0.88, "MEDIUM", "代码示例已验证，但缺少边界条件说明"),

                buildResource(studentId, "📖 动态内存分配详解", "explanation",
                    "## malloc / calloc / realloc / free\n\n### malloc\n```c\nint *p = (int*) malloc(10 * sizeof(int));\n```\n分配40字节未初始化内存。\n\n### calloc\n```c\nint *p = (int*) calloc(10, sizeof(int));\n```\n分配并**清零**40字节。\n\n### realloc\n```c\np = (int*) realloc(p, 20 * sizeof(int));\n```\n扩展内存，保留原有数据。\n\n### 重要原则\n- 每次malloc必须有对应的free\n- free后指针置NULL防止悬垂\n- 使用前检查返回值是否为NULL",
                    "内存管理", "hard", "辅导老师", "seed", "malloc,内存分配,动态内存", 0.91, "LOW", "API用法与C标准一致"),

                buildResource(studentId, "💻 单链表完整实现", "code",
                    "```c\n#include <stdio.h>\n#include <stdlib.h>\n\ntypedef struct Node {\n    int data;\n    struct Node *next;\n} Node;\n\nNode* createNode(int val) {\n    Node* n = (Node*)malloc(sizeof(Node));\n    n->data = val; n->next = NULL;\n    return n;\n}\n\nvoid insertHead(Node** head, int val) {\n    Node* n = createNode(val);\n    n->next = *head;\n    *head = n;\n}\n\nvoid printList(Node* head) {\n    while (head) {\n        printf(\"%d -> \", head->data);\n        head = head->next;\n    }\n    printf(\"NULL\\n\");\n}\n```",
                    "结构体", "hard", "出题专家", "seed", "链表,结构体,指针,数据结构", 0.85, "MEDIUM", "代码可编译运行，建议添加freeList函数避免内存泄漏"),

                buildResource(studentId, "🗺️ C语言核心知识体系思维导图", "mindmap",
                    "## C语言知识体系\n- 基础语法 → 变量、数据类型、运算符、输入输出\n- 控制结构 → if-else、switch、for、while、do-while\n- 数组与字符串 → 一维/二维数组、字符数组、字符串函数\n- 函数 → 定义调用、参数传递、递归、作用域\n- 指针 → 基础、指针与数组、指针与函数、多级指针\n- 结构体与联合体 → struct、typedef、union、enum\n- 动态内存 → malloc、calloc、realloc、free\n- 文件操作 → fopen、fread、fwrite、fclose",
                    "C语言知识体系", "medium", "课程设计师", "seed", "知识体系,思维导图,总览", 0.96, "LOW", "内容自洽，知识点覆盖完整"),

                buildResource(studentId, "📋 本周学习总结：指针进阶", "summary",
                    "## 本周学习回顾（6月13日-19日）\n\n### 学习内容\n1. **指针与数组** — 理解数组名即首元素地址\n2. **指针与函数** — 掌握传址调用实现值交换\n3. **动态内存** — 学习malloc/free基本用法\n\n### 掌握程度\n- ✅ 指针基本运算\n- ✅ 数组遍历\n- ⚠️ 指针与二维数组（需加强）\n- ❌ 指针函数与回调（待学习）\n\n### 下周计划\n深入学习结构体与链表，完成一个学生管理系统。",
                    "指针", "medium", "辅导老师", "seed", "周报,总结,指针", 0.93, "LOW", "学习内容与画像薄弱点一致"),

                buildResource(studentId, "🎯 薄弱环节分析报告", "weakness",
                    "## 薄弱环节诊断\n\n| 知识点 | 当前水平 | 目标 | 差距 |\n|--------|---------|------|------|\n| 指针 | 40/100 | 70 | 🔴 大 |\n| 内存管理 | 35/100 | 65 | 🔴 大 |\n| 结构体 | 50/100 | 70 | 🟡 中 |\n| 文件操作 | 30/100 | 55 | 🟡 中 |\n\n### 建议学习路径\n1. 先巩固指针基础（1周）\n2. 结合数组练习指针运算（3天）\n3. 学习动态内存分配（5天）\n4. 用结构体+指针实现链表（1周）",
                    "指针", "medium", "画像分析师", "seed", "薄弱点,分析,建议", 0.92, "LOW", "分析基于画像数据，建议路径合理")
            );

            // === 4. 课程推荐资源（浙大MOOC视频+PPT，4对） ===
            List<LearningResource> courseResources = List.of(
                buildCourseVideo(studentId, "浙大翁恺-C语言指针与数组（上）",
                    "介绍了指针基本概念、指针与一维数组的关系、指针运算等",
                    "https://www.icourse163.org/course/ZJU-1001614008", 83, "指针", 0.94),
                buildCoursePpt(studentId, "指针与数组课程PPT",
                    "浙江大学C语言课程配套PPT：指针与数组章节",
                    "https://www.icourse163.org/course/ZJU-1001614008", 17, "指针", 0.92),
                buildCourseVideo(studentId, "浙大翁恺-C语言结构体与链表",
                    "讲解结构体定义、typedef用法、链表基本操作（创建/遍历/插入/删除）",
                    "https://www.icourse163.org/course/ZJU-1001614008", 105, "结构体", 0.91),
                buildCoursePpt(studentId, "结构体与链表课程PPT",
                    "浙江大学C语言课程配套PPT：结构体与链表章节",
                    "https://www.icourse163.org/course/ZJU-1001614008", 22, "结构体", 0.89),
                buildCourseVideo(studentId, "浙大翁恺-C语言文件操作",
                    "介绍文件打开/关闭、顺序读写、随机访问等文件操作基础",
                    "https://www.icourse163.org/course/ZJU-1001614008", 68, "文件操作", 0.90)
            );

            // === 5. 保存全部资源 ===
            List<LearningResource> allResources = new java.util.ArrayList<>();
            allResources.addAll(agentResources);
            allResources.addAll(courseResources);
            resourceRepository.saveAll(allResources);

            log.info("student1 完整种子数据初始化完成: {}篇资源（含{}篇AI生成+{}篇课程推荐）",
                    allResources.size(), agentResources.size(), courseResources.size());

        } catch (Exception e) {
            log.warn("student1 种子数据初始化失败: {}", e.getMessage());
        }
    }

    private LearningResource buildResource(Long studentId, String title, String type,
            String content, String knowledgePoint, String difficulty,
            String generatedBy, String category, String tags,
            double score, String risk, String note) {
        LearningResource r = new LearningResource();
        r.setTitle(title);
        r.setType(type);
        r.setContent(content);
        r.setKnowledgePoint(knowledgePoint);
        r.setDifficulty(difficulty);
        r.setGeneratedBy(generatedBy);
        r.setStudentId(studentId);
        r.setCategory(category);
        r.setTags(tags);
        r.setShared(false);
        r.setHallucinationScore(score);
        r.setHallucinationRisk(risk);
        r.setHallucinationNote(note);
        return r;
    }

    private LearningResource buildCourseVideo(Long studentId, String title,
            String description, String url, int durationMin, String knowledgePoint, double score) {
        LearningResource r = new LearningResource();
        r.setTitle(title);
        r.setType("course-video");
        r.setContent("📺 **" + title + "**\n\n" + description + "\n\n⏱ 时长：" + durationMin + "分钟\n🔗 [观看视频](" + url + ")");
        r.setKnowledgePoint(knowledgePoint);
        r.setDifficulty("medium");
        r.setGeneratedBy("course-video");
        r.setStudentId(studentId);
        r.setCategory("seed");
        r.setTags("浙大,MOOC,翁恺," + knowledgePoint);
        r.setShared(false);
        r.setHallucinationScore(score);
        r.setHallucinationRisk("LOW");
        r.setHallucinationNote("浙大MOOC官方课程，内容权威可靠");
        return r;
    }

    private LearningResource buildCoursePpt(Long studentId, String title,
            String description, String url, int slides, String knowledgePoint, double score) {
        LearningResource r = new LearningResource();
        r.setTitle(title);
        r.setType("course-ppt");
        r.setContent("📊 **" + title + "**\n\n" + description + "\n\n📑 幻灯片：" + slides + "页\n🔗 [查看PPT](" + url + ")");
        r.setKnowledgePoint(knowledgePoint);
        r.setDifficulty("medium");
        r.setGeneratedBy("course-ppt");
        r.setStudentId(studentId);
        r.setCategory("seed");
        r.setTags("浙大,MOOC,PPT," + knowledgePoint);
        r.setShared(false);
        r.setHallucinationScore(score);
        r.setHallucinationRisk("LOW");
        r.setHallucinationNote("浙大MOOC官方课件");
        return r;
    }

    private static class DefaultUser {
        String username, password, nickname, role, className, department, phone, studentId;
        DefaultUser(String u, String p, String n, String r, String c, String d, String ph, String sid) {
            username = u; password = p; nickname = n; role = r; className = c; department = d; phone = ph; studentId = sid;
        }
    }

}