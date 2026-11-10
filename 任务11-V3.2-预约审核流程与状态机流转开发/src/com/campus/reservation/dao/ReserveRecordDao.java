package com.campus.reservation.dao;

import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import static com.campus.reservation.utils.FileUtil.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** ReserveRecord 数据访问：持有内存集合并维护 reserve.txt，不判断角色和业务状态。 */
public class ReserveRecordDao {
    private static final Path RESERVE_FILE = Paths.get("reserve.txt");
    private List<ReserveRecord> items;

    /** 启动时加载当前任务的文件，损坏记录交由入口报告。 */
    public ReserveRecordDao() throws IOException {
        items = readRecords();
    }

    /** 返回列表快照，调用方的增删不影响 DAO 缓存。 */
    public List<ReserveRecord> findAll() {
        return new ArrayList<>(items);
    }

    /** 按主键检索；不存在使用 Optional 表达，业务层决定失败消息。 */
    public Optional<ReserveRecord> findByRecordId(String key) {
        return items.stream().filter(Objects::nonNull)
                .filter(item -> Objects.equals(key, item.getRecordId())).findFirst();
    }

    /** 插入前检查主键，落盘成功后才发布新集合。 */
    public void insert(ReserveRecord item) throws IOException {
        if (findByRecordId(item.getRecordId()).isPresent()) {
            throw new IOException("数据主键已存在。");
        }
        List<ReserveRecord> updated = findAll();
        updated.add(item);
        saveAll(updated);
    }

    /** 按主键替换一条记录，不在原实体上修改字段。 */
    public void update(ReserveRecord item) throws IOException {
        ReserveRecord existing = findByRecordId(item.getRecordId())
                .orElseThrow(() -> new IOException("待更新记录不存在。"));
        List<ReserveRecord> updated = findAll();
        updated.set(updated.indexOf(existing), item);
        saveAll(updated);
    }

    /** 按主键删除，预约关联和权限由业务层预先校验。 */
    public void delete(String key) throws IOException {
        ReserveRecord existing = findByRecordId(key)
                .orElseThrow(() -> new IOException("待删除记录不存在。"));
        List<ReserveRecord> updated = findAll();
        updated.remove(existing);
        saveAll(updated);
    }

    /** 保存候选集合，支持业务联动的完整快照和失败补偿。 */
    public void saveAll(List<ReserveRecord> updated) throws IOException {
        List<ReserveRecord> snapshot = new ArrayList<>(updated);
        writeRecords(snapshot);
        items = snapshot;
    }

    /** 兼容八列历史记录和十列审核记录；空文件表示当前没有预约。 */
    private static List<ReserveRecord> readRecords() throws IOException {
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

    /** 统一写入十列，空审核时间保留为未知；转义避免破坏行列结构。 */
    private static void writeRecords(List<ReserveRecord> records) throws IOException {
        Path target = RESERVE_FILE.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), "reserve-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (ReserveRecord record : records) {
                    writer.write(escape(record.getRecordId()) + "\t" + escape(record.getResourceId())
                            + "\t" + escape(record.getApplicantUsername()) + "\t" + record.getApplyTime()
                            + "\t" + record.getReserveStartTime() + "\t" + record.getReserveEndTime()
                            + "\t" + (record.getStatus() == null ? "" : record.getStatus().name())
                            + "\t" + escape(record.getRemark())
                            + "\t" + escape(record.getRejectReason())
                            + "\t" + (record.getAuditTime() == null ? "" : record.getAuditTime()));
                    writer.newLine();
                }
            }
            replaceFile(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 旧 REJECT 的备注兼作驳回原因；不猜测旧记录缺失的审核时间。 */
    private static ReserveRecord parseReserveRecord(String line, int lineNumber) throws IOException {
        String[] fields = line.split("\t", -1);
        if (fields.length != 8 && fields.length != 10) {
            throw new IOException("reserve.txt 第 " + lineNumber + " 行应包含八列或十列。");
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
            String rejectReason = fields.length == 10 ? unescape(fields[8])
                    : (status == ReserveStatus.REJECT ? remark : "");
            LocalDateTime auditTime = fields.length == 10 && !fields[9].isEmpty()
                    ? LocalDateTime.parse(fields[9]) : null;
            if (recordId.trim().isEmpty() || resourceId.trim().isEmpty()
                    || applicantUsername.trim().isEmpty() || status == null
                    || reserveStartTime.isAfter(reserveEndTime)) {
                throw new IllegalArgumentException("预约字段无效");
            }
            return new ReserveRecord(recordId, resourceId, applicantUsername, applyTime,
                    reserveStartTime, reserveEndTime, status, remark, rejectReason, auditTime);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new IOException("reserve.txt 第 " + lineNumber + " 行字段格式错误。", e);
        }
    }
}
