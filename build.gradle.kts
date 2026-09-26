plugins {
    java
}

group = "com.ezstrengthen"
version = "1.1.2"

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
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveFileName.set("EzStrengthen-${project.version}.jar")
    }
}
