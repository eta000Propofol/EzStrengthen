plugins {
    java
}

group = "com.ezstrengthen"
version = "1.1.13"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper API（编译期，服务器自带）
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    // Vault API（编译期，服务器需安装 Vault + 经济插件）
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    // 测试编译也需要 Bukkit API 类型
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    // 测试模拟 Vault 经济接口
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1")
    // 单元测试：JUnit 5 + Mockito（需 inline mock maker 模拟 final 的主类）
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.24.0")
}

// 配置期捕获版本号，避免 processResources 执行期调用 Task.project（Gradle 10 起禁用）
val pluginVersion = version.toString()

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }
    jar {
        archiveFileName.set("EzStrengthen-${project.version}.jar")
    }
    test {
        useJUnitPlatform()
    }
}
