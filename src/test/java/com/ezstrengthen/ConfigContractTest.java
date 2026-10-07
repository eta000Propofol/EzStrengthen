package com.ezstrengthen;

import com.ezstrengthen.model.Affix;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置契约测试（防回归工具，深版扫描）：
 * - 代码读取的每一个配置键（getConfig().getX("字面量") 与 getMessage("字面量")）
 *   必须在打包的 config.yml 中有默认值。否则老服务器升级后（saveDefaultConfig 不覆盖
 *   已有配置）相关读取会静默落到空值/默认值——历史上 messages 段就缺过 stamp 两键。
 * - affixes 各词条段子键齐全（AffixConfig 走动态路径拼接，正则扫描不到，单独校验）；
 * - plugin.yml 的版本占位符必须已被 processResources 展开，否则 Paper 拒绝加载插件。
 * 扫描器自带锚点自检：正则若因重构失效，锚点断言会立刻失败，防止契约测试空转。
 */
class ConfigContractTest {

    /** 允许不出现在 config.yml 的键（有意只靠代码默认值兜底的键）。当前为空。 */
    private static final Set<String> ALLOWED_MISSING = Set.of();

    /** 扫描器锚点：源码中确定存在的代表性键，任何键均不得移出 config.yml。 */
    private static final Set<String> SCAN_ANCHORS = Set.of(
            "economy.costs", "combat.chance-cap", "dragon-tear.drop-amount",
            "gui.title", "messages.stamp-success", "messages.enhance-success");

    private static final Pattern CONFIG_READ = Pattern.compile(
            "getConfig\\(\\)\\s*\\.\\s*get\\w+\\(\\s*\"([^\"]+)\"\\s*[,)]");
    private static final Pattern MESSAGE_READ = Pattern.compile(
            "getMessage\\(\\s*\"([^\"]+)\"\\s*[,)]");

    private static YamlConfiguration defaultConfig;
    private static String pluginYml;
    private static Set<String> scannedKeys;

    @BeforeAll
    static void scanSourcesAndLoadResources() throws IOException, InvalidConfigurationException {
        defaultConfig = new YamlConfiguration();
        try (InputStream in = ConfigContractTest.class.getResourceAsStream("/config.yml")) {
            assertTrue(in != null, "classpath 上找不到 config.yml（主资源未打包？）");
            defaultConfig.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        try (InputStream in = ConfigContractTest.class.getResourceAsStream("/plugin.yml")) {
            assertTrue(in != null, "classpath 上找不到 plugin.yml");
            pluginYml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        scannedKeys = scanSourceKeys();
    }

    /** 扫描 src/main/java 全部源码中的配置键字面量。 */
    private static Set<String> scanSourceKeys() throws IOException {
        Path sourceRoot = Paths.get("src/main/java");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertFalse(files.isEmpty(), "未扫描到源码文件——契约测试必须从项目根目录运行");

        Set<String> keys = new TreeSet<>();
        for (Path file : files) {
            String source = Files.readString(file);
            Matcher configMatcher = CONFIG_READ.matcher(source);
            while (configMatcher.find()) {
                keys.add(configMatcher.group(1));
            }
            Matcher messageMatcher = MESSAGE_READ.matcher(source);
            while (messageMatcher.find()) {
                keys.add("messages." + messageMatcher.group(1));
            }
        }
        return keys;
    }

    @Test
    @DisplayName("扫描器自检：锚点键命中且总量不低于已知规模")
    void scannerSelfCheck() {
        for (String anchor : SCAN_ANCHORS) {
            assertTrue(scannedKeys.contains(anchor), "扫描器丢失锚点键 " + anchor + "，正则可能已失效");
        }
        // 当前源码实际规模：17 个功能键 + 25 个 messages 键 = 42
        assertTrue(scannedKeys.size() >= 40,
                "扫描到的键仅 " + scannedKeys.size() + " 个，低于已知规模，扫描器可能失效");
    }

    @Test
    @DisplayName("代码读取的每个配置键在 config.yml 中都有默认值")
    void everyReadKeyHasDefaultInConfig() {
        List<String> missing = new ArrayList<>();
        for (String key : scannedKeys) {
            if (!ALLOWED_MISSING.contains(key) && !defaultConfig.contains(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "代码读取但 config.yml 缺少默认值的键（老服务器升级后将静默失效）: " + missing);
    }

    @Test
    @DisplayName("affixes 各词条段五个子键齐全")
    void affixSectionsHaveAllSubKeys() {
        List<String> missing = new ArrayList<>();
        for (Affix affix : Affix.values()) {
            for (String sub : new String[]{"enabled", "name", "values", "extra", "duration"}) {
                String key = "affixes." + affix.getId() + "." + sub;
                if (!defaultConfig.contains(key)) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "affixes 段缺失的子键: " + missing);
    }

    @Test
    @DisplayName("plugin.yml 版本占位符已展开且主类声明正确")
    void pluginYmlIsFullyExpanded() {
        int placeholder = pluginYml.indexOf("${");
        // 注意消息为急切求值：仅当确实存在占位符时才截取上下文
        assertFalse(placeholder >= 0, "plugin.yml 存在未展开的占位符，Paper 将拒绝加载: "
                + (placeholder >= 0 ? pluginYml.substring(Math.max(0, placeholder - 40)) : ""));
        assertTrue(pluginYml.contains("main: com.ezstrengthen.EzStrengthen"), "plugin.yml 主类声明异常");
        assertTrue(pluginYml.contains("name: EzStrengthen"), "plugin.yml 插件名声明异常");
    }
}
