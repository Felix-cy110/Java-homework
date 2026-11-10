import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.utils.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 在隔离文件中验证旧八列迁移、新十列往返与损坏数据拦截。 */
public class PersistenceTest {
    private static int checks;
    private static final Path FILE = Paths.get("reserve.txt");

    /** 文件只在全新的测试目录创建，损坏用例不会接触正式数据。 */
    public static void main(String[] args) throws Exception {
        if (Files.exists(FILE)) { throw new IllegalStateException("拒绝覆盖已有预约文件。"); }
        String special = "原因\\说明\t制表\n换行\r回车\uFEFF";
        String escaped = FileUtil.escape(special);
        String common = "room\talice\t2020-01-01T09:00\t2020-01-01T10:00\t2020-01-01T11:00\t";
        String legacy = "\uFEFFpending\t" + common + "PENDING\t原备注\n"
                + "passed\t" + common + "PASS\t原备注\n"
                + "rejected\t" + common + "REJECT\t" + escaped + "\n"
                + "cancelled\t" + common + "CANCEL\t原备注\n";
        write(legacy);
        byte[] original = Files.readAllBytes(FILE);
        ReserveRecordDao dao = new ReserveRecordDao();
        check(dao.findAll().size() == 4, "读取四种历史状态");
        check(Arrays.equals(original, Files.readAllBytes(FILE)), "加载不自动改写旧文件");
        for (ReserveRecord record : dao.findAll()) {
            check(record.getAuditTime() == null, "不伪造历史审核时间");
            check((record.getStatus() == ReserveStatus.REJECT ? special : "").equals(record.getRejectReason()),
                    "仅旧 REJECT 备注转为原因");
        }
        check(special.equals(dao.findByRecordId("rejected").get().getRemark()), "转换保留旧备注");
        dao.saveAll(dao.findAll());
        for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
            check(line.split("\t", -1).length == 10, "统一写为十列");
        }
        ReserveRecordDao reopened = new ReserveRecordDao();
        check(reopened.findAll().size() == 4, "迁移后重启无丢失");
        check(special.equals(reopened.findByRecordId("rejected").get().getRejectReason()), "迁移特殊字符完整");
        byte[] converted = Files.readAllBytes(FILE);
        reopened.saveAll(reopened.findAll());
        check(Arrays.equals(converted, Files.readAllBytes(FILE)), "重跑不重复导入或更改内容");

        LocalDateTime audited = LocalDateTime.of(2026, 11, 10, 12, 0, 1, 123456789);
        ReserveRecord record = reopened.findByRecordId("rejected").get();
        record.setRemark("学生申请");
        record.setRejectReason(special);
        record.setAuditTime(audited);
        reopened.update(record);
        ReserveRecord reloaded = new ReserveRecordDao().findByRecordId("rejected").get();
        check(audited.equals(reloaded.getAuditTime()), "审核时间纳秒精度往返");
        check("学生申请".equals(reloaded.getRemark()) && special.equals(reloaded.getRejectReason()), "备注与原因独立往返");
        check(reloaded.toString().contains(special) && reloaded.toString().contains(audited.toString()), "显示审核信息");

        // 迁移期间允许两种列数共存；截断或损坏的字段必须明确失败。
        String valid = "modern\t" + common + "PASS\t备注\t\t" + audited;
        write(legacy + valid + "\n");
        check(new ReserveRecordDao().findAll().size() == 5, "新旧格式混合加载");
        List<String> invalid = Arrays.asList(
                "nine\t" + common + "PASS\t备注\t原因",
                valid + "\t多余列",
                "date\t" + common + "REJECT\t备注\t原因\t错误时间",
                "status\t" + common + "UNKNOWN\t备注\t\t",
                "\t" + common + "PASS\t备注\t\t",
                valid + "\n" + valid);
        for (String malformed : invalid) {
            write(malformed + "\n");
            byte[] damaged = Files.readAllBytes(FILE);
            try {
                new ReserveRecordDao();
                throw new AssertionError("损坏文件不应加载成功");
            } catch (IOException expected) {
                check(expected.getMessage().contains("reserve.txt 第"), "损坏错误含文件和行号");
            }
            check(Arrays.equals(damaged, Files.readAllBytes(FILE)), "失败加载不破坏原文件");
        }
        write("");
        check(new ReserveRecordDao().findAll().isEmpty(), "空预约文件正常加载");
        System.out.println("PASS PersistenceTest: " + checks + " checks");
    }

    /** 测试使用与生产一致的 UTF-8 编码。 */
    private static void write(String content) throws IOException {
        Files.write(FILE, content.getBytes(StandardCharsets.UTF_8));
    }

    /** 统计真实通过的检查。 */
    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }
}
