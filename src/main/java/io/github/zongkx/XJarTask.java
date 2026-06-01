package io.github.zongkx;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class XJarTask extends DefaultTask {

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Input
    @Optional
    public abstract ListProperty<String> getIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getExcludes();

    @Input
    @Optional
    public abstract Property<String> getGoPath();

    @InputFile
    public abstract RegularFileProperty getSourceJar();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getTargetJar();

    public XJarTask() {
        getIncludes().convention(List.of("**/*.class"));
    }

    @TaskAction
    public void execute() throws Exception {
        // 💡 1. 动态定义并创建本地解耦工具目录：项目根目录/gradle/tools/xjar-cli.jar
        File toolsDir = new File(getProject().getRootDir(), "gradle/tools");
        if (!toolsDir.exists()) {
            toolsDir.mkdirs();
        }
        File cliJar = new File(toolsDir, "xjar-cli.jar");

        // 💡 2. 释放静态资源：每次执行时将插件内部集成了完整资源与依赖的 cli 包释放/覆盖到本地 tools 目录
        getLogger().lifecycle("🚀 [XJar-Plugin] 正在从插件沙箱壳中释放工具至: " + cliJar.getAbsolutePath());
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("xjar-core/xjar-cli.jar")) {
            if (is == null) {
                throw new GradleException("插件骨架损坏：未能在内部解获到预存的 xjar-core/xjar-cli.jar");
            }
            try (FileOutputStream fos = new FileOutputStream(cliJar)) {
                is.transferTo(fos);
            }
        }

        // 3. 准备加固源文件和输出目标
        File inputJar = getSourceJar().get().getAsFile();
        if (!inputJar.exists()) {
            throw new GradleException("未找到待加固的源 JAR 包: " + inputJar.getAbsolutePath());
        }

        File targetFile;
        if (getTargetJar().isPresent()) {
            targetFile = getTargetJar().get().getAsFile();
        } else {
            String originalPath = inputJar.getAbsolutePath();
            targetFile = new File(originalPath.substring(0, originalPath.length() - 4) + ".x.jar");
        }

        getLogger().lifecycle("🚀 [Gradle 调度] 正在拉起绝对纯净的本地独立 JVM 进程进行代码加固...");

        // 4. 执行本地命令，将 Gradle 的类加载器污染肉体隔绝
        var execResult = getExecOperations().exec(spec -> {
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-Djdk.util.jar.versionless=true");
            command.add("-jar");
            command.add(cliJar.getAbsolutePath());

            // 灌入给 XJarMain 的四大命令行参数
            command.add(inputJar.getAbsolutePath());
            command.add(targetFile.getAbsolutePath());
            command.add(String.join(",", getIncludes().getOrElse(List.of())));
            command.add(String.join(",", getExcludes().getOrElse(List.of())));

            spec.setCommandLine(command);
            spec.setStandardOutput(System.out);
            spec.setErrorOutput(System.err);
            spec.setIgnoreExitValue(true); // 统一由下方 exitValue 进行逻辑熔断
        });

        if (execResult.getExitValue() != 0) {
            throw new GradleException("本地独立 XJar 进程执行异常，加固失败终止。");
        }

        // 5. 后置编译 Go 启动器
        File jarDir = targetFile.getParentFile();
        File goFile = new File(jarDir, "xjar.go");
        if (goFile.exists()) {
            getLogger().lifecycle("检测到解密 Go 源码，正在启动本地 Go 环境进行编译...");
            compileGoLauncher(jarDir, goFile);
        }
    }

    private void compileGoLauncher(File workDir, File goFile) throws Exception {
        String goExecutable = checkIsWindows() ? "go.exe" : "go";
        if (getGoPath().isPresent() && !getGoPath().get().trim().isEmpty()) {
            File customGo = new File(getGoPath().get());
            if (customGo.exists()) {
                goExecutable = customGo.getAbsolutePath();
            }
        }

        List<String> command = List.of(goExecutable, "build", goFile.getName());
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                getLogger().lifecycle("[Go Build] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            goFile.delete();
        } else {
            throw new GradleException("Go 编译失败，退出码: " + exitCode);
        }
    }

    private boolean checkIsWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}