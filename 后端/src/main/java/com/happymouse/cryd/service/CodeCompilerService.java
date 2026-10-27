package com.happymouse.cryd.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C 代码编译校验服务
 * <p>
 * 对 AI 生成的 type=code 资源调用 gcc 编译器验证是否可编译通过。
 * 如果系统未安装 gcc，自动降级为基本语法规则校验（不阻塞流程）。
 * <p>
 * 配置项 application.yml：<br>
 * - cryd.compiler.gcc-path: gcc 编译器路径（默认 "gcc"）<br>
 * - cryd.compiler.timeout-seconds: 编译超时秒数（默认 15）<br>
 * - cryd.compiler.enabled: 是否启用编译校验（默认 true）
 */
@Service
public class CodeCompilerService {

    private static final Logger log = LoggerFactory.getLogger(CodeCompilerService.class);

    /** Markdown 中 ```c ... ``` 代码块提取正则 */
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```c\\s*\\n?(.*?)```", Pattern.DOTALL);

    @Value("${cryd.compiler.gcc-path:gcc}")
    private String gccPath = "gcc";

    @Value("${cryd.compiler.timeout-seconds:15}")
    private int timeoutSeconds = 15;

    @Value("${cryd.compiler.enabled:true}")
    private boolean enabled = true;

    private volatile Boolean gccAvailable = null;

    /**
     * 编译校验一条 C 代码资源
     *
     * @param markdownContent 资源的 content 字段（Markdown，含 ```c 代码块）
     * @return 编译结果
     */
    public CompileResult validate(String markdownContent) {
        if (!enabled) {
            return CompileResult.skipped("编译校验已禁用");
        }

        // 1. 提取 C 代码
        String cCode = extractCCode(markdownContent);
        if (cCode == null || cCode.isBlank()) {
            return CompileResult.failed("未找到可编译的 C 代码块（需要 ```c ... ``` 包裹）");
        }

        // 2. 基本语法规则校验（始终执行）
        SyntaxCheck syntaxResult = basicSyntaxCheck(cCode);
        if (syntaxResult.hasFatalErrors()) {
            return CompileResult.failed("语法规则校验失败: " + syntaxResult.reason());
        }

        // 3. gcc 编译校验
        if (!isGccAvailable()) {
            // gcc 不可用时降级：语法规则通过即算 PASS
            log.debug("gcc 未安装，跳过编译校验（仅依赖语法规则）");
            return CompileResult.passed("语法规则校验通过（gcc 未安装，跳过编译）");
        }

        return compileWithGcc(cCode, syntaxResult);
    }

    /**
     * 检查 gcc 是否可用（缓存结果）
     */
    private boolean isGccAvailable() {
        if (gccAvailable != null) return gccAvailable;
        try {
            Process p = new ProcessBuilder(gccPath, "--version")
                    .redirectErrorStream(true)
                    .start();
            // 使用无超时 waitFor + 手动超时控制
            boolean finished = false;
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int code = p.exitValue();
                    finished = true;
                    gccAvailable = code == 0;
                    break;
                } catch (IllegalThreadStateException ignored) {
                    Thread.sleep(100);
                }
            }
            if (!finished) {
                p.destroyForcibly();
                gccAvailable = false;
            }
        } catch (Exception e) {
            gccAvailable = false;
        }
        log.info("gcc 编译器检测: path={}, available={}", gccPath, gccAvailable);
        return gccAvailable;
    }

    /**
     * gcc 编译校验
     */
    private CompileResult compileWithGcc(String cCode, SyntaxCheck syntaxResult) {
        Path tempDir = null;
        Path sourceFile = null;
        try {
            tempDir = Files.createTempDirectory("cryd-gcc-");
            sourceFile = tempDir.resolve("check.c");
            Files.writeString(sourceFile, cCode);

            ProcessBuilder pb = new ProcessBuilder(
                    gccPath, "-fsyntax-only", "-Wall", "-std=c11",
                    "-o", tempDir.resolve("a.out").toString(),
                    sourceFile.toString()
            );
            pb.redirectErrorStream(true);

            Process p = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 4096) break; // 截断过长的编译输出
                }
            }

            boolean finished = false;
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    p.exitValue();
                    finished = true;
                    break;
                } catch (IllegalThreadStateException ignored) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (!finished) {
                p.destroyForcibly();
                return CompileResult.failed("编译超时（>" + timeoutSeconds + "s）");
            }

            int exitCode = p.exitValue();
            if (exitCode == 0) {
                return CompileResult.passed(output.toString().trim());
            } else {
                // 提取关键错误行
                return CompileResult.failed(extractKeyErrors(output.toString()));
            }
        } catch (IOException e) {
            log.warn("gcc 编译异常", e);
            return CompileResult.failed("编译异常: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (sourceFile != null) try { Files.deleteIfExists(sourceFile); } catch (IOException ignored) {}
            if (tempDir != null) try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
        }
    }

    /**
     * 从 Markdown 内容中提取 C 代码
     */
    public static String extractCCode(String markdown) {
        if (markdown == null) return null;
        Matcher m = CODE_BLOCK.matcher(markdown);
        if (m.find()) return m.group(1).trim();
        // 如果没有代码块标记，返回null而不是整段内容
        return null;
    }

    /**
     * 提取 gcc 输出的关键错误行（前5条）
     */
    private String extractKeyErrors(String gccOutput) {
        String[] lines = gccOutput.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 保留 error/warning 行，跳过位置信息
            if (trimmed.contains(": error:") || trimmed.contains(": warning:")) {
                if (count > 0) sb.append(" | ");
                sb.append(trimmed.substring(trimmed.indexOf(':') + 1).trim());
                count++;
                if (count >= 5) break;
            }
        }
        if (sb.isEmpty()) {
            // 如果没有标准格式的行，取前 3 行非空
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" | ");
                    sb.append(line.trim());
                    if (sb.length() > 300) break;
                }
            }
        }
        return sb.isEmpty() ? "编译失败（无详细错误信息）" : sb.toString();
    }

    /**
     * 基本语法规则校验（不依赖 gcc 的静态检查）
     */
    private SyntaxCheck basicSyntaxCheck(String code) {
        int openBraces = 0;
        int openParens = 0;
        boolean inString = false;
        boolean inComment = false;
        char prev = 0;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (inComment) {
                if (c == '\n') inComment = false;
                continue;
            }
            if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '/' && !inString) {
                inComment = true;
                continue;
            }

            if (c == '"' && prev != '\\' && !inComment) {
                inString = !inString;
            }
            if (inString) { prev = c; continue; }

            if (c == '{') openBraces++;
            if (c == '}') openBraces--;
            if (c == '(') openParens++;
            if (c == ')') openParens--;

            prev = c;
        }

        // 危险函数检测
        boolean hasGets = code.matches("(?s).*\\bgets\\s*\\(.*");
        boolean hasFflushStdin = code.matches("(?s).*fflush\\s*\\(\\s*stdin\\s*\\).*");

        return new SyntaxCheck(openBraces, openParens, hasGets, hasFflushStdin);
    }

    /**
     * 编译并运行 C 代码，采集 stdout
     *
     * @param cCode          C 源代码
     * @param stdin          传给程序的标准输入
     * @param timeoutSeconds 运行超时秒数
     * @return 运行结果
     */
    public RunResult run(String cCode, String stdin, int timeoutSeconds) {
        if (!enabled) {
            return RunResult.gccNotAvailable();
        }
        if (!isGccAvailable()) {
            return RunResult.gccNotAvailable();
        }

        Path tempDir = null;
        Path sourceFile = null;
        Path exeFile = null;
        try {
            // 1. 创建临时目录，写入 main.c
            tempDir = Files.createTempDirectory("cryd-run-");
            sourceFile = tempDir.resolve("main.c");
            Files.writeString(sourceFile, cCode);

            // Windows 下可执行文件是 main.exe，Linux 下是 main
            String exeName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "main.exe" : "main";
            exeFile = tempDir.resolve(exeName);

            // 2. 编译：gcc -O0 -std=c11 -Wall -o <tempDir>/main <tempDir>/main.c
            ProcessBuilder compilePb = new ProcessBuilder(
                    gccPath, "-O0", "-std=c11", "-Wall",
                    "-o", exeFile.toString(),
                    sourceFile.toString()
            );
            compilePb.redirectErrorStream(true);

            Process compileProcess = compilePb.start();
            StringBuilder compileOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    compileOutput.append(line).append("\n");
                    if (compileOutput.length() > 4096) break;
                }
            }

            // 编译超时控制
            boolean compileFinished = false;
            long compileDeadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while (System.currentTimeMillis() < compileDeadline) {
                try {
                    int compileExitCode = compileProcess.exitValue();
                    compileFinished = true;
                    if (compileExitCode != 0) {
                        return RunResult.compileFailed(extractKeyErrors(compileOutput.toString()));
                    }
                    break;
                } catch (IllegalThreadStateException ignored) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (!compileFinished) {
                compileProcess.destroyForcibly();
                return RunResult.compileFailed("编译超时（>" + timeoutSeconds + "s）");
            }

            // 3. 运行编译产物 —— 应用基础沙箱限制
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder runPb;
            if (isWindows) {
                // Windows 无法用 ulimit，仅隔离工作目录与环境
                runPb = new ProcessBuilder(exeFile.toString());
            } else {
                // Linux/Mac 用 sh + ulimit 限制：虚拟内存 256MB、CPU 时间 timeoutSeconds 秒
                // 作为 deadline 超时的兜底，防止 fork 炸弹耗尽主机内存
                runPb = new ProcessBuilder("sh", "-c",
                        "ulimit -v 262144 -t " + timeoutSeconds + "; exec '" + exeFile + "'");
            }
            runPb.directory(tempDir.toFile());
            runPb.redirectErrorStream(false);
            // 清理环境变量，仅保留进程执行必需的 PATH（Windows 还需 SystemRoot）
            Map<String, String> env = runPb.environment();
            env.clear();
            env.put("PATH", System.getenv("PATH"));
            if (isWindows && System.getenv("SystemRoot") != null) {
                env.put("SystemRoot", System.getenv("SystemRoot"));
            }
            Process runProcess = runPb.start();

            // 写入 stdin 后立刻关闭，否则进程可能等待输入不退出
            if (stdin != null && !stdin.isEmpty()) {
                runProcess.getOutputStream().write(stdin.getBytes());
            }
            runProcess.getOutputStream().close();

            // 用 daemon 线程分别读取 stdout 和 stderr
            StringBuilder stdoutBuilder = new StringBuilder();
            StringBuilder stderrBuilder = new StringBuilder();

            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuilder.append(line).append("\n");
                        if (stdoutBuilder.length() > 65536) break;
                    }
                } catch (IOException ignored) {}
            }, "run-stdout-reader");
            stdoutReader.setDaemon(true);

            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuilder.append(line).append("\n");
                        if (stderrBuilder.length() > 65536) break;
                    }
                } catch (IOException ignored) {}
            }, "run-stderr-reader");
            stderrReader.setDaemon(true);

            stdoutReader.start();
            stderrReader.start();

            // 超时控制：deadline + Thread.sleep(100) 轮询
            long runStart = System.currentTimeMillis();
            boolean runFinished = false;
            long runDeadline = runStart + timeoutSeconds * 1000L;
            while (System.currentTimeMillis() < runDeadline) {
                try {
                    runProcess.exitValue();
                    runFinished = true;
                    break;
                } catch (IllegalThreadStateException ignored) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            long durationMs = System.currentTimeMillis() - runStart;

            if (!runFinished) {
                runProcess.destroyForcibly();
                // 等待读取线程结束
                try { stdoutReader.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                try { stderrReader.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                return new RunResult(false, true, "", -1,
                        stdoutBuilder.toString(), stderrBuilder.toString(), durationMs, true);
            }

            // 等待读取线程结束
            try { stdoutReader.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try { stderrReader.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            int exitCode = runProcess.exitValue();
            boolean ok = exitCode == 0;
            return new RunResult(ok, true, "", exitCode,
                    stdoutBuilder.toString(), stderrBuilder.toString(), durationMs, false);

        } catch (IOException e) {
            log.warn("编译运行异常", e);
            return RunResult.compileFailed("编译运行异常: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (sourceFile != null) try { Files.deleteIfExists(sourceFile); } catch (IOException ignored) {}
            if (exeFile != null) try { Files.deleteIfExists(exeFile); } catch (IOException ignored) {}
            if (tempDir != null) try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
        }
    }

    // ─── 内部类 ───

    /** 编译结果 */
    public record CompileResult(boolean passed, boolean skipped, String message) {
        public static CompileResult passed(String msg) {
            return new CompileResult(true, false, msg);
        }
        public static CompileResult failed(String msg) {
            return new CompileResult(false, false, msg);
        }
        public static CompileResult skipped(String msg) {
            return new CompileResult(false, true, msg);
        }
    }

    /** 编译+运行结果 */
    public record RunResult(
        boolean ok,           // 整体是否成功（编译+运行）
        boolean compileOk,    // 编译是否通过
        String compileError,  // 编译错误信息
        int exitCode,         // 运行退出码
        String stdout,        // 标准输出
        String stderr,        // 标准错误
        long durationMs,      // 运行耗时毫秒
        boolean timedOut      // 是否超时
    ) {
        public static RunResult compileFailed(String error) {
            return new RunResult(false, false, error, -1, "", "", 0, false);
        }
        public static RunResult gccNotAvailable() {
            return new RunResult(false, false, "gcc not available", -1, "", "", 0, false);
        }
    }

    /** 语法检查结果 */
    private record SyntaxCheck(int braces, int parens, boolean hasGets, boolean hasFflushStdin) {
        boolean hasFatalErrors() {
            return braces != 0 || parens != 0 || hasGets || hasFflushStdin;
        }
        String reason() {
            StringBuilder sb = new StringBuilder();
            if (braces > 0) sb.append("缺少 ").append(braces).append(" 个 } ");
            if (braces < 0) sb.append("多余的 } ×").append(-braces).append(" ");
            if (parens > 0) sb.append("缺少 ").append(parens).append(" 个 ) ");
            if (parens < 0) sb.append("多余的 ) ×").append(-parens).append(" ");
            if (hasGets) sb.append("使用了不安全的 gets() ");
            if (hasFflushStdin) sb.append("使用了 fflush(stdin) ");
            return sb.toString().trim();
        }
    }
}
