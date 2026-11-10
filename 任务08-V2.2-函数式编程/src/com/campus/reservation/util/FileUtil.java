package com.campus.reservation.util;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.User;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 文件工具：集中读写用户、资源及当前阶段的最小预约关联，封装 IO 细节。 */
public final class FileUtil {
    private static final Path USER_FILE = Paths.get("user.txt");
    private static final Path RESOURCE_FILE = Paths.get("resource.txt");
    private static final Path RESERVE_FILE = Paths.get("reserve.txt");

    /** 工具类仅提供静态方法，无需创建实例。 */
    private FileUtil() {
    }

    /** 首次创建空文件，并将全部合法记录还原为用户集合。 */
    public static List<User> loadUsers() throws IOException {
        ensureFile(USER_FILE);
        List<User> users = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(USER_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // 兼容编辑器保存的 UTF-8 BOM；空行不表示用户记录。
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isEmpty()) {
                    continue;
                }
                User user = parseUser(line, lineNumber);
                if (!usernames.add(user.getUsername())) {
                    throw new IOException("user.txt 第 " + lineNumber + " 行账号重复。");
                }
                users.add(user);
            }
        }
        return users;
    }

    /** 将完整集合写入同目录临时文件，关闭流后再替换正式文件。 */
    public static void saveUsers(List<User> users) throws IOException {
        Path target = USER_FILE.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), "user-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (User user : users) {
                    writer.write(escape(user.getUsername()) + "\t" + escape(user.getPassword())
                            + "\t" + escape(user.getRole()) + "\t" + user.getRegisterTime());
                    writer.newLine();
                }
            }
            replaceFile(temporary, target);
        } finally {
            // 替换成功后临时文件已不存在，失败时只清理本次创建的文件。
            Files.deleteIfExists(temporary);
        }
    }

    /** 加载全部资源，拒绝重复 ID 和损坏记录，防止以不完整数据继续操作。 */
    public static List<CampusResource> loadResources() throws IOException {
        ensureFile(RESOURCE_FILE);
        List<CampusResource> resources = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(RESOURCE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isEmpty()) {
                    continue;
                }
                CampusResource resource = parseResource(line, lineNumber);
                if (!ids.add(resource.getId())) {
                    throw new IOException("resource.txt 第 " + lineNumber + " 行 ID 重复。");
                }
                resources.add(resource);
            }
        }
        return resources;
    }

    /** 保存候选资源列表，写入完整临时文件后替换，失败时保留原文件。 */
    public static void saveResources(List<CampusResource> resources) throws IOException {
        Path target = RESOURCE_FILE.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), "resource-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (CampusResource resource : resources) {
                    writer.write(escape(resource.getId()) + "\t" + escape(resource.getName())
                            + "\t" + escape(resource.getType()) + "\t" + escape(resource.getStatus())
                            + "\t" + escape(resource.getLocation()));
                    writer.newLine();
                }
            }
            replaceFile(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 任务 6 的预约关联仅保存资源 ID；删除时重读，任务 9 再升级完整记录。 */
    public static Set<String> loadReservedResourceIds() throws IOException {
        ensureFile(RESERVE_FILE);
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(RESERVE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    String id = unescape(line);
                    if (id.trim().isEmpty() || id.indexOf('\t') >= 0
                            || id.indexOf('\r') >= 0 || id.indexOf('\n') >= 0) {
                        throw new IllegalArgumentException("无效资源 ID");
                    }
                    ids.add(id);
                } catch (IllegalArgumentException e) {
                    throw new IOException("reserve.txt 第 " + lineNumber + " 行格式错误。", e);
                }
            }
        }
        return ids;
    }

    /** 按五列还原资源，文件校验不依赖控制台业务方法。 */
    private static CampusResource parseResource(String line, int lineNumber) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 5) {
            throw new IOException("resource.txt 第 " + lineNumber + " 行应包含五列。");
        }
        try {
            String id = unescape(fields[0]);
            String name = unescape(fields[1]);
            String type = unescape(fields[2]);
            String status = unescape(fields[3]);
            String location = unescape(fields[4]);
            if (id.trim().isEmpty() || id.indexOf('\t') >= 0 || id.indexOf('\r') >= 0
                    || id.indexOf('\n') >= 0 || name.trim().isEmpty()
                    || !("教室".equals(type) || "实验室".equals(type)
                    || "设备".equals(type) || "场地".equals(type))
                    || !("AVAILABLE".equals(status) || "OCCUPIED".equals(status)
                    || "DISABLED".equals(status))) {
                throw new IllegalArgumentException("资源字段无效");
            }
            return new CampusResource(id, name, type, status, location);
        } catch (IllegalArgumentException e) {
            throw new IOException("resource.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }

    /** 优先原子替换，不支持时沿用普通替换，用户与资源文件共用此策略。 */
    private static void replaceFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 仅在首次运行时创建指定文件，已有内容保持原样。 */
    private static void ensureFile(Path file) throws IOException {
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IOException(file.getFileName() + " 不是普通文件。");
            }
            return;
        }
        Files.createFile(file);
    }

    /** 按四列解析记录；格式损坏时报告行号，不回显账号密码原文。 */
    private static User parseUser(String line, int lineNumber) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 4) {
            throw new IOException("user.txt 第 " + lineNumber + " 行应包含四列。");
        }
        try {
            String username = unescape(fields[0]);
            String password = unescape(fields[1]);
            String role = unescape(fields[2]);
            if (username.isEmpty() || password.isEmpty()
                    || !("admin".equals(role) || "student".equals(role))) {
                throw new IllegalArgumentException("字段缺失或角色无效");
            }
            return new User(username, password, role, LocalDateTime.parse(fields[3]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IOException("user.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }

    /** 转义分隔符、换行和 BOM 字符，避免字段首字符被误认成文件标记。 */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\uFEFF", "\\uFEFF");
    }

    /** 逐字符还原转义，避免把原本的反斜杠加字母误当作制表符。 */
    private static String unescape(String value) {
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
