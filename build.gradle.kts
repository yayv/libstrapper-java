plugins {
    `java-library`
}

group = "top.x0a"
version = "0.4.0"
description = "libstrapper —— WEB API 参数契约校验，valueFormat / dataStruct 规范的 Java 实现"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

sourceSets {
    // 演示代码单独一个源集，不会进 strapper.jar
    create("demo") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
    // 性能对比单独一个源集：第三方 JSON 库只在这里出现，库本体仍然零依赖
    create("bench") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
    // 生成 Markdown 版 API 文档的 doclet，纯构建期工具，不进 strapper.jar
    create("doclet")
}

dependencies {
    // 库本体刻意保持零第三方依赖：JSON 解析与正则都用 JDK 自带能力。
    // 下面三个只进 bench 源集，不进 strapper.jar，也不会传递给使用方。
    "benchImplementation"("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    "benchImplementation"("com.google.code.gson:gson:2.14.0")
    "benchImplementation"("com.alibaba.fastjson2:fastjson2:2.0.65")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

// 只配置内置的 javadoc 任务；下面的 apiDoc 用的是自定义 doclet，认不得 -Xdoclint
tasks.named<Javadoc>("javadoc") {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "libstrapper",
            "Implementation-Version" to project.version,
        )
    }
}

// 没有引入 JUnit，自检用例是一个带 main 的普通类，见 src/test/java/.../SelfTest.java
tasks.test {
    enabled = false
}

val selfTest = tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "运行零依赖的自检用例"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "top.x0a.strapper.SelfTest"
}

tasks.check {
    dependsOn(selfTest)
}

// 从源码注释生成单个 Markdown 文件的 API 文档，放进 lib/
// 标准 javadoc 产出的是 frame + 几十个 HTML，要开浏览器才看得了；
// 一个 md 文件可以直接在编辑器、GitHub、IDE 里读，也能跟着代码一起提交、一起 diff。
val apiDoc = tasks.register<Javadoc>("apiDoc") {
    group = "documentation"
    description = "从源码注释生成 lib/API.md"
    source = sourceSets.main.get().allJava
    // 注意：Javadoc 任务会清空目标目录，所以先生成到 build 下，再由 packageJar 复制进 lib/
    setDestinationDir(layout.buildDirectory.dir("apiDoc").get().asFile)
    dependsOn(tasks.named("docletClasses"))
    (options as org.gradle.external.javadoc.CoreJavadocOptions).apply {
        doclet = "top.x0a.tools.MarkdownDoclet"
        docletpath = sourceSets["doclet"].output.files.toList()
        encoding = "UTF-8"
        addStringOption("mdfile", "API.md")
        addStringOption("mdtitle", "libstrapper API")
        addStringOption("mdsubtitle",
            "`top.x0a` ${project.version} —— WEB API 参数契约校验 + 独立的 JSON 解析/写出")
        addBooleanOption("quiet", true)
    }
    // 生成的是 md 不是 html，Gradle 默认的产物检查对不上，关掉
    outputs.upToDateWhen { false }
}

// 把打好的 jar 放一份到 lib/，方便别的项目直接引用
tasks.register<Copy>("packageJar") {
    group = "build"
    description = "把 jar 和 sources jar 复制到 lib/"
    from(tasks.jar, tasks.named("sourcesJar"))
    into(layout.projectDirectory.dir("lib"))
}

// 把 bench 的 classpath 写到文件里，好让每个库各跑一个干净的 JVM
tasks.register("benchClasspath") {
    group = "verification"
    description = "导出性能对比所需的 classpath"
    val out = layout.buildDirectory.file("bench-cp.txt")
    val cp = sourceSets["bench"].runtimeClasspath
    dependsOn(cp)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText(cp.asPath)
    }
}

tasks.register<JavaExec>("runBench") {
    group = "verification"
    description = "把内置 JSON 解析与 Jackson / Gson / fastjson2 做对比"
    classpath = sourceSets["bench"].runtimeClasspath
    mainClass = "top.x0a.strapper.bench.JsonBench"
}

tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "运行 demo，演示这个库怎么用"
    classpath = sourceSets["demo"].runtimeClasspath
    mainClass = "top.x0a.strapper.demo.Demo"
}
