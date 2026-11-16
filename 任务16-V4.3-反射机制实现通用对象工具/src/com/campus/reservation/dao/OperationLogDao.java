package com.campus.reservation.dao;

import com.campus.reservation.common.Result;
import com.campus.reservation.entity.OperationLogRecord;
import com.campus.reservation.utils.FileUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 日志 DAO：饿汉单例、UTF-8 追加持久化；故障只影响日志，不中断原业务。 */
public final class OperationLogDao {
    private static final Path LOG_FILE = Paths.get("operation_log.txt");
    private static final OperationLogDao INSTANCE = new OperationLogDao();
    private final List<OperationLogRecord> items = new ArrayList<>();
    private String loadError;
    private String saveError;

    /** 启动读取全部历史；损坏文件保留原样并停止写入，防止静默覆盖。 */
    private OperationLogDao() {
        try {
            FileUtil.ensureFile(LOG_FILE);
            List<OperationLogRecord> loaded = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            try (BufferedReader reader = Files.newBufferedReader(LOG_FILE, StandardCharsets.UTF_8)) {
                String line;
                int number = 0;
                while ((line = reader.readLine()) != null) {
                    line = FileUtil.stripBom(line, ++number);
                    String[] f = line.split("\t", -1);
                    if (f.length != 8) { throw new IOException("日志字段数量错误"); }
                    for (int i = 0; i < f.length; i++) { f[i] = FileUtil.unescape(f[i]); }
                    OperationLogRecord record = new OperationLogRecord(f[0], f[1], LocalDateTime.parse(f[2]),
                            f[3], f[4], f[5], f[6], f[7]);
                    validate(record);
                    if (!ids.add(record.getLogId())) { throw new IOException("日志编号重复"); }
                    loaded.add(record);
                }
            }
            items.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            loadError = "操作日志加载失败，请检查 operation_log.txt 的格式或访问权限；原文件未覆盖。";
        }
    }

    /** 获取唯一日志对象；初始化错误通过 Result 暴露，不破坏原业务启动。 */
    public static OperationLogDao getInstance() { return INSTANCE; }

    /** 追加一条日志，落盘成功后发布内存副本；失败尝试回退到追加前长度。 */
    public Result<Void> save(OperationLogRecord record) {
        if (loadError != null) { return Result.fail(loadError); }
        try {
            validate(record);
            if (items.stream().anyMatch(item -> item.getLogId().equals(record.getLogId()))) {
                return reportFailure("操作日志编号重复，未追加重复记录。");
            }
            OperationLogRecord snapshot = copy(record);
            String line = Stream.of(snapshot.getLogId(), snapshot.getUsername(), snapshot.getOperateTime().toString(),
                    snapshot.getMethodFullName(), snapshot.getOperationDesc(), snapshot.getParams(),
                    snapshot.getResultInfo(), snapshot.getExceptionMsg())
                    .map(FileUtil::escape).collect(Collectors.joining("\t")) + "\n";
            if (!Files.isRegularFile(LOG_FILE)) { throw new IOException("日志文件不可写"); }
            try (FileChannel channel = FileChannel.open(LOG_FILE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long length = channel.size();
                // 兼容人工保存的末行无换行文件，避免下一条日志粘连。
                String prefix = "";
                if (length > 0) {
                    ByteBuffer last = ByteBuffer.allocate(1);
                    channel.position(length - 1);
                    channel.read(last);
                    if (last.array()[0] != '\n') { prefix = "\n"; }
                }
                channel.position(length);
                try {
                    ByteBuffer data = ByteBuffer.wrap((prefix + line).getBytes(StandardCharsets.UTF_8));
                    while (data.hasRemaining()) { channel.write(data); }
                    channel.force(true);
                } catch (IOException e) {
                    try { channel.truncate(length); channel.force(true); }
                    catch (IOException rollback) { loadError = "操作日志写入及回退失败，请先检查 operation_log.txt。"; }
                    throw e;
                }
            }
            items.add(snapshot);
            saveError = null;
            return Result.success("日志已保存。", null);
        } catch (IOException | RuntimeException e) {
            return reportFailure("操作日志保存失败，原业务结果不受影响；请检查 operation_log.txt 后重试。");
        }
    }

    /** 返回深拷贝快照；读取错误或最近丢失的日志必须明确提示，不能假装完整。 */
    public Result<List<OperationLogRecord>> findAll() {
        if (loadError != null) { return Result.fail(loadError); }
        if (saveError != null) { return Result.fail(saveError); }
        return Result.success("操作日志查询成功。", items.stream().map(OperationLogDao::copy).collect(Collectors.toList()));
    }

    /** 切面组装异常也进入可查询的故障状态，禁止向控制台打印堆栈。 */
    public Result<Void> reportFailure(String message) {
        saveError = message;
        return Result.fail(message);
    }

    /** 严格检查字段完整性，不修造缺失账号、时间或方法信息。 */
    private static void validate(OperationLogRecord record) {
        if (record == null || blank(record.getLogId()) || blank(record.getUsername())
                || record.getOperateTime() == null || blank(record.getMethodFullName())
                || blank(record.getOperationDesc()) || record.getParams() == null
                || record.getResultInfo() == null || record.getExceptionMsg() == null) {
            throw new IllegalArgumentException("日志字段不完整");
        }
    }

    /** 必填文本只用于格式检查，不改变原值。 */
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    /** 隔离可变实体，防止调用方修改已保存或已查询的日志。 */
    private static OperationLogRecord copy(OperationLogRecord r) {
        return new OperationLogRecord(r.getLogId(), r.getUsername(), r.getOperateTime(), r.getMethodFullName(),
                r.getOperationDesc(), r.getParams(), r.getResultInfo(), r.getExceptionMsg());
    }
}
