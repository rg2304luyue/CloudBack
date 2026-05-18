package org.cloudback.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动加载项目根目录的 .env 文件到 Spring Environment。
 * 查找顺序：当前工作目录 → 向上遍历父目录。
 * 支持 ${KEY} 变量引用展开。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        if (dotenv.entries().isEmpty()) {
            return;
        }

        Map<String, String> raw = new HashMap<>();
        dotenv.entries().forEach(e -> raw.put(e.getKey(), e.getValue()));

        Map<String, Object> resolved = new HashMap<>();
        for (var entry : raw.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue(), raw));
        }

        // 添加到最末尾（低优先级），不覆盖已有的系统环境变量和命令行参数
        environment.getPropertySources()
                .addLast(new MapPropertySource("dotenv", resolved));
    }

    private String resolve(String value, Map<String, String> vars) {
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = vars.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
