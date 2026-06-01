package io.github.zongkx;

import io.github.zongkx.XJarExtension;
import io.github.zongkx.XJarTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;

public class XjarPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // 1. 创建给用户配置的 DSL 闭包: xjar { ... }
        XJarExtension extension = project.getExtensions().create("xjar", XJarExtension.class);

        // 2. 注册我们的 xjar 任务
        project.getTasks().register("xjar", XJarTask.class, task -> {
            // 属性延迟绑定
            task.getIncludes().set(extension.getIncludes());
            task.getExcludes().set(extension.getExcludes());
            task.getGoPath().set(extension.getGoPath());
            task.getTargetJar().set(extension.getTargetJar());

            // 核心逻辑：如果用户配置了 sourceJar，用配置的；否则默认读取 Java 插件生产的 jar 文件
            task.getSourceJar().set(extension.getSourceJar().orElse(
                    project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                           .flatMap(Jar::getArchiveFile)
            ));
            
            // 让我们的任务在生成普通 jar 包之后执行
            task.mustRunAfter(project.getTasks().named(JavaPlugin.JAR_TASK_NAME));
        });
    }
}