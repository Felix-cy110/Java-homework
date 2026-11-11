package com.campus.reservation.dao;

import com.campus.reservation.entity.CampusResource;
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

/** CampusResource 数据访问：持有内存集合并维护 resource.txt，不判断角色和业务状态。 */
public class ResourceDao {
    private static final Path RESOURCE_FILE = Paths.get("resource.txt");
    private static final ResourceDao INSTANCE;
    private static final IOException INITIALIZATION_ERROR;

    // 饿汉初始化只读取一次；保留错误供入口报告具体文件。
    static {
        ResourceDao instance = null;
        IOException error = null;
        try {
            instance = new ResourceDao();
        } catch (IOException e) {
            error = e;
        }
        INSTANCE = instance;
        INITIALIZATION_ERROR = error;
    }
    private List<CampusResource> items;

    /** 启动时加载当前任务的文件，损坏记录交由入口报告。 */
    private ResourceDao() throws IOException {
        items = readRecords();
    }

    /** 获取唯一实例；加载失败时保留原始 IO 错误。 */
    public static ResourceDao getInstance() throws IOException {
        if (INITIALIZATION_ERROR != null) {
            throw INITIALIZATION_ERROR;
        }
        return INSTANCE;
    }

    /** 返回列表快照，调用方的增删不影响 DAO 缓存。 */
    public List<CampusResource> findAll() {
        return new ArrayList<>(items);
    }

    /** 按主键检索；不存在使用 Optional 表达，业务层决定失败消息。 */
    public Optional<CampusResource> findById(String key) {
        return items.stream().filter(Objects::nonNull)
                .filter(item -> Objects.equals(key, item.getId())).findFirst();
    }

    /** 插入前检查主键，落盘成功后才发布新集合。 */
    public void insert(CampusResource item) throws IOException {
        if (findById(item.getId()).isPresent()) {
            throw new IOException("数据主键已存在。");
        }
        List<CampusResource> updated = findAll();
        updated.add(item);
        saveAll(updated);
    }

    /** 按主键替换一条记录，不在原实体上修改字段。 */
    public void update(CampusResource item) throws IOException {
        CampusResource existing = findById(item.getId())
                .orElseThrow(() -> new IOException("待更新记录不存在。"));
        List<CampusResource> updated = findAll();
        updated.set(updated.indexOf(existing), item);
        saveAll(updated);
    }

    /** 按主键删除，预约关联和权限由业务层预先校验。 */
    public void delete(String key) throws IOException {
        CampusResource existing = findById(key)
                .orElseThrow(() -> new IOException("待删除记录不存在。"));
        List<CampusResource> updated = findAll();
        updated.remove(existing);
        saveAll(updated);
    }

    /** 保存候选集合，支持业务联动的完整快照和失败补偿。 */
    public void saveAll(List<CampusResource> updated) throws IOException {
        List<CampusResource> snapshot = new ArrayList<>(updated);
        writeRecords(snapshot);
        items = snapshot;
    }

    /** 加载全部资源，拒绝重复 ID 和损坏记录。 */
    private static List<CampusResource> readRecords() throws IOException {
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
    private static void writeRecords(List<CampusResource> resources) throws IOException {
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
}
