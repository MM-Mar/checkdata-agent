package com.checkdata.agent.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zip 解压工具。
 */
public final class ZipUtils {

    private ZipUtils() {
    }

    /**
     * 安全解压 zip 到目标目录（防 Zip Slip 路径穿越攻击）。
     *
     * @param zipStream zip 输入流
     * @param targetDir 解压目标目录（不存在会自动创建）
     * @throws IOException 解压失败或检测到非法路径
     */
    public static void extract(InputStream zipStream, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path dest = targetDir.resolve(entry.getName()).normalize();
                if (!dest.startsWith(targetDir)) {
                    throw new IOException("zip 包含非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
