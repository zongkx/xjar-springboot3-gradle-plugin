package io.xjar;

import java.util.Arrays;
import java.util.UUID;

public class XJarMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("用法错误！缺少必要参数。正确用法: java -jar xjar-cli.jar <srcPath> <targetPath> [includes] [excludes]");
            System.exit(1);
        }

        String sourcePath = args[0];
        String targetPath = args[1];
        String includesStr = args.length > 2 ? args[2] : "**/*.class";
        String excludesStr = args.length > 3 ? args[3] : "";

        System.out.println("[XJar-CLI] 开始对目标文件进行外部物理隔离加固: " + sourcePath);

        try {
            // 在这个绝对干净的、由 java -jar 启动的独立 JVM 里，
            // 它的 Classpath 只有它自己，绝不包含任何高版本 Spring 6 物理包
            XEncryption xEncryption = XCryptos.encryption()
                    .from(sourcePath)
                    .use(UUID.randomUUID().toString());

            if (!includesStr.trim().isEmpty()) {
                Arrays.stream(includesStr.split(",")).forEach(xEncryption::include);
            }
            if (!excludesStr.trim().isEmpty()) {
                Arrays.stream(excludesStr.split(",")).forEach(xEncryption::exclude);
            }

            xEncryption.to(targetPath);
            System.out.println("[XJar-CLI] 加固成功！输出至: " + targetPath);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[XJar-CLI] 加固发生致命错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}