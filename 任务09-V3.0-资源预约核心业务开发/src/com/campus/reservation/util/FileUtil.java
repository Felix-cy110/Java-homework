package com.campus.reservation.util;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
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

/** 文件工具：集中读写用户、资源和完整预约记录，封装 IO 细节。 */
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
                line = stripBom(line, lineNumber);
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

    /** 将完整用户集合写入临时文件后替换正式文件。 */
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
            Files.deleteIfExists(temporary);
        }
    }

    /** 加载全部资源，拒绝重复 ID 和损坏记录。 */
    public static List<CampusResource> loadResources() throws IOException {
        ensureFile(RESOURCE_FILE);
        List<CampusResource> resources = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(RESOURCE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = stripBom(line, lineNumber);
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

    /** 保存候选资源列表，成功后才替换正式文件。 */
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

    /** 启动时读取八列预约记录；空文件表示当前没有预约。 */
    public static List<ReserveRecord> loadReserveRecords() throws IOException {
        ensureFile(RESERVE_FILE);
        List<ReserveRecord> records = new ArrayList<>();
        Set<String> recordIds = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(RESERVE_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = stripBom(line, lineNumber);
                if (line.isEmpty()) {
                    continue;
                }
                ReserveRecord record = parseReserveRecord(line, lineNumber);
                if (!recordIds.add(record.getRecordId())) {
                    throw new IOException("reserve.txt 第 " + lineNumber + " 行记录编号重复。");
                }
                records.add(record);
            }
        }
        return records;
    }

    /** 以八列转义格式保存预约记录，保证制表符和换行不会破坏文件结构。 */
    public static void saveReserveRecords(List<ReserveRecord> records) throws IOException {
        Path target = RESERVE_FILE.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), "reserve-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (ReserveRecord record : records) {
                    writer.write(escape(record.getRecordId()) + "\t" + escape(record.getResourceId())
                            + "\t" + escape(record.getApplicantUsername()) + "\t" + record.getApplyTime()
                            + "\t" + record.getReserveStartTime() + "\t" + record.getReserveEndTime()
                            + "\t" + (record.getStatus() == null ? "" : record.getStatus().name())
                            + "\t" + escape(record.getRemark()));
                    writer.newLine();
                }
            }
            replaceFile(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
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
            if (id.trim().isEmpty() || name.trim().isEmpty()
                    || !("教室".equals(type) || "实验室".equals(type)
                    || "设备".equals(type) || "场地".equals(type))
                    || !("AVAILABLE".equals(status) || "OCCUPIED".equals(status)
                    || "DISABLED".equals(status))) {
                throw new IllegalArgumentException("资源字段无效");
            }
            return new CampusResource(id, name, type, status, location);
        } catch (RuntimeException e) {
            throw new IOException("resource.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }

    /** 按八列解析预约记录，格式错误时报告行号且不加载残缺数据。 */
    private static ReserveRecord parseReserveRecord(String line, int lineNumber) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 8) {
            throw new IOException("reserve.txt 第 " + lineNumber + " 行应包含八列。");
        }
        try {
            String recordId = unescape(fields[0]);
            String resourceId = unescape(fields[1]);
            String applicantUsername = unescape(fields[2]);
            LocalDateTime applyTime = LocalDateTime.parse(fields[3]);
            LocalDateTime reserveStartTime = LocalDateTime.parse(fields[4]);
            LocalDateTime reserveEndTime = LocalDateTime.parse(fields[5]);
            ReserveStatus status = ReserveStatus.valueOf(fields[6]);
            String remark = unescape(fields[7]);
            if (recordId.trim().isEmpty() || resourceId.trim().isEmpty()
                    || applicantUsername.trim().isEmpty() || status == null
                    || reserveStartTime.isAfter(reserveEndTime)) {
                throw new IllegalArgumentException("预约字段无效");
            }
            return new ReserveRecord(recordId, resourceId, applicantUsername, applyTime,
                    reserveStartTime, reserveEndTime, status, remark);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new IOException("reserve.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }

    /** 按四列解析用户，密码不回显到错误信息。 */
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
        } catch (RuntimeException e) {
            throw new IOException("user.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }

    /** 原子替换优先，不支持时回退到普通替换。 */
    private static void replaceFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 仅首次运行创建文件，已有内容保持不变。 */
    private static void ensureFile(Path file) throws IOException {
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IOException(file.getFileName() + " 不是普通文件。");
            }
            return;
        }
        Files.createFile(file);
    }

    /** 去除首行 BOM，兼容常见编辑器保存的 UTF-8 文件。 */
    private static String stripBom(String line, int lineNumber) {
        return lineNumber == 1 && line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    /** 转义分隔符、换行和 BOM，避免字段破坏行结构。 */
    private static String escape(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\uFEFF", "\\uFEFF");
    }

    /** 逐字符还原转义，避免误把普通反斜杠序列当成分隔符。 */
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
