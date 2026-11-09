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

/** 用户文件工具：集中处理文件创建、读写及文本转换，调用方不接触 IO 流。 */
public final class FileUtil {
    private static final Path USER_FILE = Paths.get("user.txt");

    /** 工具类仅提供静态方法，无需创建实例。 */
    private FileUtil() {
    }

    /** 首次创建空文件，并将全部合法记录还原为用户集合。 */
    public static List<User> loadUsers() throws IOException {
        ensureUserFile();
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
            // 优先原子替换；文件系统不支持时使用普通替换。
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // 替换成功后临时文件已不存在，失败时只清理本次创建的文件。
            Files.deleteIfExists(temporary);
        }
    }

    /** 仅在首次运行时创建文件，已有内容保持原样。 */
    private static void ensureUserFile() throws IOException {
        if (Files.exists(USER_FILE)) {
            if (!Files.isRegularFile(USER_FILE)) {
                throw new IOException("user.txt 不是普通文件。");
            }
            return;
        }
        Files.createFile(USER_FILE);
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

    /** 转义分隔符、换行和 BOM 字符，避免账号首字符被误认成文件标记。 */
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
