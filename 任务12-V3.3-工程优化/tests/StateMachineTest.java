import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.dao.ResourceDao;
import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.ReserveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 独立验证状态矩阵、审核元数据、权限与失败后不变性。 */
public class StateMachineTest {
    private static int checks;
    private static final LocalDateTime PAST = LocalDateTime.of(2020, 1, 1, 10, 0);

    /** 使用历史时间验证取消没有额外时间门槛，测试目录必须为空。 */
    public static void main(String[] args) throws Exception {
        for (String file : Arrays.asList("user.txt", "resource.txt", "reserve.txt")) {
            if (Files.exists(Paths.get(file))) {
                throw new IllegalStateException("拒绝覆盖已有业务数据。");
            }
        }
        User admin = new User("admin", "demo", "admin", PAST);
        User alice = new User("alice", "demo", "student", PAST);
        User bob = new User("bob", "demo", "student", PAST);
        ResourceDao resources = ResourceDao.getInstance();
        ReserveRecordDao records = ReserveRecordDao.getInstance();
        ReserveService service = ReserveService.getInstance();
        ReserveStatus[] states = ReserveStatus.values();
        boolean[][] legal = {
            {false, true, true, true},
            {false, false, false, true},
            {false, false, false, false},
            {false, false, false, false}
        };
        for (int i = 0; i < states.length; i++) {
            check(!states[i].canTransitionTo(null), "空目标拒绝");
            for (int j = 0; j < states.length; j++) {
                ReserveStatus from = states[i];
                ReserveStatus to = states[j];
                check(from.canTransitionTo(to) == legal[i][j], "枚举矩阵 " + from + " -> " + to);
                reset(resources, records, from);
                byte[] originalFile = Files.readAllBytes(Paths.get("reserve.txt"));
                LocalDateTime before = LocalDateTime.now();
                Result<ReserveRecord> result = to == ReserveStatus.CANCEL
                        ? service.cancelReservation(alice, "record")
                        : service.auditReservation(admin, "record", to, "审核原因");
                check(result.isSuccess() == legal[i][j], "服务矩阵 " + from + " -> " + to);
                ReserveRecord saved = ReserveRecordDao.getInstance().findByRecordId("record").get();
                if (legal[i][j]) {
                    check(saved.getStatus() == to, "合法流转更新缓存");
                    check("学生原备注".equals(saved.getRemark()), "不覆盖申请备注");
                    if (to == ReserveStatus.CANCEL) {
                        check(from == ReserveStatus.PASS ? PAST.equals(saved.getAuditTime())
                                : saved.getAuditTime() == null, "取消保留原审核时间");
                    } else {
                        check(saved.getAuditTime() != null && !saved.getAuditTime().isBefore(before)
                                && !saved.getAuditTime().isAfter(LocalDateTime.now()), "自动记录真实审核时间");
                        check((to == ReserveStatus.REJECT ? "审核原因" : "").equals(saved.getRejectReason()),
                                "只有驳回保存原因");
                    }
                    check((to == ReserveStatus.PASS ? "OCCUPIED" : "AVAILABLE").equals(
                            ResourceDao.getInstance().findById("room").get().getStatus()), "联动资源状态");
                } else {
                    fail(result, "非法流转友好失败");
                    check(Arrays.equals(originalFile, Files.readAllBytes(Paths.get("reserve.txt"))),
                            "非法流转不写文件");
                    check(saved.getStatus() == from, "非法流转不改状态");
                }
            }
        }

        reset(resources, records, ReserveStatus.PENDING);
        fail(service.auditReservation(admin, "record", null, ""), "空审核决定");
        fail(service.auditReservation(null, "record", ReserveStatus.PASS, ""), "未登录审核");
        fail(service.auditReservation(alice, "record", ReserveStatus.PASS, ""), "学生审核");
        fail(service.cancelReservation(bob, "record"), "取消他人记录");
        fail(service.cancelReservation(admin, "record"), "管理员代取消");
        fail(service.cancelReservation(null, "record"), "未登录取消");
        fail(service.auditReservation(admin, "missing", ReserveStatus.PASS, ""), "审核不存在记录");
        fail(service.auditReservation(admin, " ", ReserveStatus.PASS, ""), "空记录编号");
        fail(service.queryPendingReservations(alice), "学生不能查看待审核队列");
        fail(service.queryPendingReservations(null), "未登录不能查看待审核队列");

        // 四种状态并存，待审核队列只能包含 PENDING；查询结果可直接显示原因。
        records.saveAll(Arrays.asList(record("pending", ReserveStatus.PENDING),
                record("passed", ReserveStatus.PASS), record("rejected", ReserveStatus.REJECT),
                record("cancelled", ReserveStatus.CANCEL)));
        Result<List<ReserveRecord>> queue = service.queryPendingReservations(admin);
        check(queue.isSuccess() && queue.getData().size() == 1
                && "pending".equals(queue.getData().get(0).getRecordId()), "待审核队列不混入其他状态");
        check(service.queryMyReservations(bob).getData().isEmpty(), "空个人列表");
        check(service.queryMyReservations(alice).getData().stream().anyMatch(r ->
                r.getStatus() == ReserveStatus.REJECT && r.toString().contains("历史原因")), "学生可见驳回原因");
        check(service.auditReservation(admin, "pending", ReserveStatus.REJECT, null).isSuccess(), "驳回原因可留空");
        check(service.queryPendingReservations(admin).getData().isEmpty(), "审核后退出待审核队列");
        check(ReserveRecordDao.getInstance().findByRecordId("pending").get().getAuditTime() != null, "空原因仍记录审核时间");

        // 空状态在服务边界被拦截，不触发 NullPointerException。
        reset(resources, records, null);
        fail(service.auditReservation(admin, "record", ReserveStatus.PASS, ""), "空当前状态审核");
        fail(service.cancelReservation(alice, "record"), "空当前状态取消");

        System.out.println("PASS StateMachineTest: " + checks + " checks");
    }

    /** 每一组合使用相同的独立起点，避免前一次状态污染下一次断言。 */
    private static void reset(ResourceDao resources, ReserveRecordDao records, ReserveStatus status) throws IOException {
        resources.saveAll(Arrays.asList(new CampusResource("room", "教室", "教室",
                status == ReserveStatus.PASS ? "OCCUPIED" : "AVAILABLE", "测试楼")));
        records.saveAll(Arrays.asList(record("record", status)));
    }

    /** 构造过去的预约，审核过的记录保留可比较的原审核时间。 */
    private static ReserveRecord record(String id, ReserveStatus status) {
        return new ReserveRecord(id, "room", "alice", PAST, PAST, PAST.plusHours(1), status,
                "学生原备注", status == ReserveStatus.REJECT ? "历史原因" : "",
                status == ReserveStatus.PASS || status == ReserveStatus.REJECT ? PAST : null);
    }

    /** 失败必须使用统一消息且不携带成功数据。 */
    private static void fail(Result<?> result, String label) {
        check(!result.isSuccess() && result.getData() == null && result.getMsg() != null
                && !result.getMsg().isEmpty(), label);
    }

    /** 明确执行断言，无需开启 JVM assertions。 */
    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
        checks++;
    }

}
