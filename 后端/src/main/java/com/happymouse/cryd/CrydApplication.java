package com.happymouse.cryd;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrydApplication {

    public static void main(String[] args) {
        // 加载 .env 文件中的环境变量
        try {
            // 尝试多个可能的 .env 路径
            String[] searchPaths = {
                System.getProperty("user.dir") + "/../.env",
                System.getProperty("user.dir") + "/.env"
            };
            Dotenv dotenv = null;
            for (String path : searchPaths) {
                java.nio.file.Path envPath = java.nio.file.Paths.get(path);
                if (java.nio.file.Files.exists(envPath)) {
                    System.out.println("[INFO] 加载 .env: " + envPath.toAbsolutePath());
                    dotenv = Dotenv.configure()
                            .directory(envPath.getParent().toString())
                            .filename(".env")
                            .load();
                    break;
                }
            }
            if (dotenv == null) {
                dotenv = Dotenv.configure().load();
            }
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
                // 首次打印确认加载
                if (entry.getKey().startsWith("SPARK_")) {
                    System.out.println("[INFO] .env loaded: " + entry.getKey() + "=" +
                        (entry.getKey().contains("SECRET") || entry.getKey().contains("KEY") ?
                         entry.getValue().substring(0, Math.min(8, entry.getValue().length())) + "..." : entry.getValue()));
                }
            });
        } catch (Exception e) {
            System.out.println("[WARN] .env 文件未找到或加载失败: " + e.getMessage());
        }

        SpringApplication.run(CrydApplication.class, args);
        System.out.println("""

                ========================================
                  师承智导 多智能体学习系统启动成功！
                  DeepSeek V4 Pro + Spring Boot
                ========================================
                """);
    }
}
