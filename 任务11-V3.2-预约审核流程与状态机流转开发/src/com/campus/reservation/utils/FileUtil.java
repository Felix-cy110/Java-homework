package com.campus.reservation.utils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 通用文件工具：只处理文件替换、创建和文本转义，不依赖业务实体。 */
public final class FileUtil {
    /** 静态工具不允许实例化。 */
    private FileUtil() {
    }

    /** 原子替换优先，不支持时回退到普通替换。 */
    public static void replaceFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 仅首次运行创建文件，已有内容保持不变。 */
    public static void ensureFile(Path file) throws IOException {
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IOException(file.getFileName() + " 不是普通文件。");
            }
            return;
        }
        Files.createFile(file);
    }

    /** 去除首行 BOM，兼容常见编辑器保存的 UTF-8 文件。 */
    public static String stripBom(String line, int lineNumber) {
        return lineNumber == 1 && line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    /** 转义分隔符、换行和 BOM，避免字段破坏行结构。 */
    public static String escape(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\uFEFF", "\\uFEFF");
    }

    /** 逐字符还原转义，避免误把普通反斜杠序列当成分隔符。 */
    public static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++i >= value.length()) {
                throw new IllegalArgumentException("不完整的转义");
            }
            switch (value.charAt(i)) {
                case '\\': result.append('\\'); break;
                case 't': result.append('\t'); break;
                case 'r': result.append('\r'); break;
                case 'n': result.append('\n'); break;
                case 'u':
                    if (!value.startsWith("FEFF", i + 1)) {
                        throw new IllegalArgumentException("未知 Unicode 转义");
                    }
                    result.append('\uFEFF');
                    i += 4;
                    break;
                default: throw new IllegalArgumentException("未知转义");
            }
        }
        return result.toString();
    }
}
