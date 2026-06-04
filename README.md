## 简介
这个插件是对  xjar 提供了 springboot3 支持


```groovy
tasks.named<XJarTask>("xjar") {
    
    sourceJar.set(tasks.bootJar.flatMap { it.archiveFile })
    // 设置加密后的目标 Jar 路径
    targetJar.set(layout.buildDirectory.file("libs/${project.name}-encrypted.jar"))
    // 你的其他配置
    includes.set(listOf("/com/example/**/*.class"))
    
    excludes.set(emptyList())
}

```
