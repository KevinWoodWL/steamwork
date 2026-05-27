import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    `java-library`
    idea
    id("com.gradleup.shadow") version "9.0.0"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("io.freefair.lombok") version "9.5.0"
}

group = "io.github.steamwork"

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
    maven("https://repo.xenondevs.xyz/releases") {
        name = "InvUI"
    }
    maven("https://maven.pvphub.me/tofaa") {
        name = "EntityLib"
    }
}

val rebarVersion = project.properties["rebar.version"] as String
val pylonVersion = project.properties["pylon.version"] as String
val minecraftVersion = project.properties["minecraft.version"] as String

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    // rebar / pylon 尚未推到公开 Maven，直接引用本地构建 jar。
    compileOnly(files("../rebar/rebar/build/libs/rebar-1.0.0-SNAPSHOT.jar"))
    compileOnly(files("../pylon/build/libs/pylon-1.0.0-SNAPSHOT.jar"))
    // 本地 jar 不带 transitive，所以这里要手动给 steamwork 暴露 Kotlin stdlib。
    // 运行时 Paper 通过 rebar 的 paperLibraryApi 加载，所以仍是 compileOnly。
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    compileOnly("xyz.xenondevs.invui:invui:2.1.0") { isTransitive = false }
}

java {
    // rebar 0.39 / pylon 0.37 的 jar 是用 Java 25 编译的（class version 69）。
    // steamwork 跟上到 Java 25 才能链接它们；toolchain 自动找本机 JDK 25。
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

// 构建时间后缀（格式：yyyyMMdd-HHmm），加在 jar 文件名末尾方便辨别多次构建。
val buildTimestamp: String = LocalDateTime.now()
    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))

tasks.shadowJar {
    mergeServiceFiles()
    archiveBaseName = project.name
    archiveClassifier = null
    archiveFileName.set("${project.name}-${project.version}.jar")
}

tasks.named<Jar>("sourcesJar") {
    archiveFileName.set("${project.name}-${project.version}-sources.jar")
}

// 原始 jar（无 shadow 依赖）对玩家无用，禁用以免和 shadowJar 混淆。
tasks.named<Jar>("jar") {
    enabled = false
}

bukkit {
    name = "Steamwork"
    main = "io.github.steamwork.Steamwork"
    version = project.version.toString()
    apiVersion = minecraftVersion
    depend = listOf("Rebar", "Pylon")
    softDepend = listOf("MonolithLib")
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
}

tasks.runServer {
    downloadPlugins {
        github("pylonmc", "rebar", rebarVersion, "rebar-$rebarVersion.jar")
    }
    maxHeapSize = "4G"
    minecraftVersion(minecraftVersion)
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
