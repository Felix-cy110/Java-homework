package com.campus.reservation.dao;

import com.campus.reservation.entity.User;
import static com.campus.reservation.utils.FileUtil.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** User 数据访问：持有内存集合并维护 user.txt，不判断角色和业务状态。 */
public class UserDao {
    private static final Path USER_FILE = Paths.get("user.txt");
    private static final UserDao INSTANCE;
    private static final IOException INITIALIZATION_ERROR;

    // 饿汉初始化只读取一次；保留错误供入口报告具体文件。
    static {
        UserDao instance = null;
        IOException error = null;
        try {
            instance = new UserDao();
        } catch (IOException e) {
            error = e;
        }
        INSTANCE = instance;
        INITIALIZATION_ERROR = error;
    }
    private List<User> items;

    /** 启动时加载当前任务的文件，损坏记录交由入口报告。 */
    private UserDao() throws IOException {
        items = readRecords();
    }

    /** 获取唯一实例；加载失败时保留原始 IO 错误。 */
    public static UserDao getInstance() throws IOException {
        if (INITIALIZATION_ERROR != null) {
            throw INITIALIZATION_ERROR;
        }
        return INSTANCE;
    }

    /** 返回列表快照，调用方的增删不影响 DAO 缓存。 */
    public List<User> findAll() {
        return new ArrayList<>(items);
    }

    /** 按主键检索；不存在使用 Optional 表达，业务层决定失败消息。 */
    public Optional<User> findByUsername(String key) {
        return items.stream().filter(Objects::nonNull)
                .filter(item -> Objects.equals(key, item.getUsername())).findFirst();
    }

    /** 插入前检查主键，落盘成功后才发布新集合。 */
    public void insert(User item) throws IOException {
        if (findByUsername(item.getUsername()).isPresent()) {
            throw new IOException("数据主键已存在。");
        }
        List<User> updated = findAll();
        updated.add(item);
        saveAll(updated);
    }

    /** 按主键替换一条记录，不在原实体上修改字段。 */
    public void update(User item) throws IOException {
        User existing = findByUsername(item.getUsername())
                .orElseThrow(() -> new IOException("待更新记录不存在。"));
        List<User> updated = findAll();
        updated.set(updated.indexOf(existing), item);
        saveAll(updated);
    }

    /** 按主键删除，预约关联和权限由业务层预先校验。 */
    public void delete(String key) throws IOException {
        User existing = findByUsername(key)
                .orElseThrow(() -> new IOException("待删除记录不存在。"));
        List<User> updated = findAll();
        updated.remove(existing);
        saveAll(updated);
    }

    /** 保存候选集合，支持业务联动的完整快照和失败补偿。 */
    public void saveAll(List<User> updated) throws IOException {
        List<User> snapshot = new ArrayList<>(updated);
        writeRecords(snapshot);
        items = snapshot;
    }

    /** 首次创建空文件，并将全部合法记录还原为用户集合。 */
    private static List<User> readRecords() throws IOException {
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
    private static void writeRecords(List<User> users) throws IOException {
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
}
