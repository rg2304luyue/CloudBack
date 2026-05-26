package org.cloudback.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Path envFile = findEnvFile();
            if (envFile == null) {
                System.err.println("[dotenv] .env not found, wd=" + System.getProperty("user.dir"));
                return;
            }
            System.err.println("[dotenv] Loading " + envFile);

            Dotenv dotenv = Dotenv.configure()
                    .directory(envFile.getParent().toString())
                    .ignoreIfMissing()
                    .load();

            if (dotenv.entries().isEmpty()) {
                System.err.println("[dotenv] Empty entries");
                return;
            }

            Map<String, String> raw = new HashMap<>();
            dotenv.entries().forEach(e -> raw.put(e.getKey(), e.getValue()));

            Map<String, Object> resolved = new HashMap<>();
            for (var entry : raw.entrySet()) {
                resolved.put(entry.getKey(), resolve(entry.getValue(), raw));
            }

            environment.getPropertySources()
                    .addLast(new MapPropertySource("dotenv", resolved));

            System.err.println("[dotenv] Registered " + resolved.size() + " entries, VM_HOST=" + resolved.get("VM_HOST"));
        } catch (Exception e) {
            System.err.println("[dotenv] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Path findEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path parent = dir.getParent();
            if (parent == null) break;
            dir = parent;
        }
        return null;
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
