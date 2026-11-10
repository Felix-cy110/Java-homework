import com.campus.reservation.dao.ResourceDao;
import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.dao.UserDao;
import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.ReserveRecord;
import com.campus.reservation.entity.ReserveStatus;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import com.campus.reservation.service.ResourceService;
import com.campus.reservation.service.ReserveService;
import com.campus.reservation.service.UserService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 原生 Java 回归：直接调用 Service 验证业务边界，必须在全新隔离目录运行。 */
public class RegressionTest {
    private static int checks;

    /** 从空库覆盖业务闭环、持久化、越权和写入失败补偿。 */
    public static void main(String[] args) throws Exception {
        for (String file : Arrays.asList("user.txt", "resource.txt", "reserve.txt")) {
            if (Files.exists(Paths.get(file))) {
                throw new IllegalStateException("测试目录必须没有业务数据，拒绝覆盖已有文件。");
            }
        }
        UserDao ud = new UserDao();
        ResourceDao rd = new ResourceDao();
        ReserveRecordDao bd = new ReserveRecordDao();
        UserService users = new UserService(ud);
        ResourceService resources = new ResourceService(rd, bd);
        ReserveService reservations = new ReserveService(rd, bd);
        ok(users.initializeDefaultAdministrator(), "初始化管理员");
        ok(users.initializeDefaultAdministrator(), "再次初始化");
        check(ud.findAll().size() == 1, "不重复创建管理员");
        User admin = ok(users.loginUser("admin", "123456"), "管理员登录");
        fail(users.registerUser(" ", "123456"), "空账号");
        fail(users.registerUser("alice", "12345"), "短密码");
        User alice = ok(users.registerUser("alice", "123456"), "注册学生");
        User bob = ok(users.registerUser("bob", "123456"), "注册另一学生");
        check("student".equals(alice.getRole()) && alice.getRegisterTime() != null, "学生默认字段");
        fail(users.registerUser("alice", "123456"), "重复账号");
        fail(users.loginUser("alice", "wrong"), "错误密码");
        fail(users.loginUser("missing", "123456"), "账号不存在");
        fail(users.loginUser("alice", ""), "空密码");
        ok(new UserService(new UserDao()).loginUser("alice", "123456"), "重新加载后登录");

        CampusResource room = ok(resources.addResource(admin, "Alpha教室", "教室", "测试楼"), "新增资源");
        String id = room.getId();
        CampusResource other = ok(resources.addResource(admin, "Beta设备", "设备", ""), "新增第二资源");
        fail(resources.addResource(alice, "越权", "教室", ""), "学生新增资源");
        fail(resources.addResource(admin, "", "教室", ""), "资源名称为空");
        fail(resources.addResource(admin, "非法类型", "其他", ""), "资源类型非法");
        fail(resources.updateResource(alice, id, "改名", "教室", "", "AVAILABLE"), "学生修改资源");
        fail(resources.updateResource(admin, id, "改名", "教室", "", "UNKNOWN"), "资源状态非法");
        fail(resources.deleteResource(alice, id), "学生删除资源");
        fail(resources.deleteResource(admin, "missing"), "删除不存在资源");
        List<CampusResource> sorted = ok(resources.queryResources(admin, ""), "管理员查全部");
        check(sorted.size() == 2 && sorted.get(0).getId().compareTo(sorted.get(1).getId()) < 0, "按 ID 升序");
        check(ok(resources.queryResources(alice, "aLPHa"), "模糊查询").size() == 1, "忽略大小写筛选");
        ok(resources.updateResource(admin, other.getId(), "Beta设备", "设备", "", "DISABLED"), "停用资源");
        check(ok(resources.queryResources(alice, ""), "学生查可预约资源").size() == 1, "过滤停用资源");
        fail(resources.queryResources(null, ""), "未登录查询");

        LocalDateTime start = LocalDateTime.of(2099, 1, 1, 10, 0);
        LocalDateTime end = start.plusHours(1);
        String remark = "测试\\备注\t制表\n换行\r回车\uFEFF";
        fail(reservations.submitReservation(admin, id, start, end, ""), "管理员不得代提交");
        fail(reservations.submitReservation(alice, "missing", start, end, ""), "预约资源不存在");
        fail(reservations.submitReservation(alice, other.getId(), start, end, ""), "停用资源不可预约");
        fail(reservations.submitReservation(alice, id, end, start, ""), "开始晚于结束");
        fail(reservations.submitReservation(alice, id, start, null, ""), "时间为空");
        fail(reservations.submitReservation(alice, id, start.minusYears(150), end.minusYears(150), ""), "结束时间已过");
        ReserveRecord first = ok(reservations.submitReservation(alice, id, start, end, remark), "提交预约");
        ReserveRecord pending = ok(reservations.submitReservation(bob, id, start, end, ""), "允许重叠待审核");
        check(first.getStatus() == ReserveStatus.PENDING, "默认 PENDING");
        check(ok(reservations.queryMyReservations(alice), "学生查询自己的预约").size() == 1, "不展示别人预约");
        check(ok(reservations.queryAllReservations(admin), "管理员查询全部预约").size() == 2, "展示全部申请人");
        fail(reservations.queryAllReservations(alice), "学生不得查询全部");
        fail(reservations.queryMyReservations(admin), "管理员不得使用学生入口");
        fail(reservations.cancelReservation(bob, first.getRecordId()), "越权取消");
        fail(reservations.cancelReservation(admin, first.getRecordId()), "管理员不得代取消");
        fail(reservations.cancelReservation(alice, "missing"), "取消不存在记录");
        fail(reservations.auditReservation(alice, first.getRecordId(), ReserveStatus.PASS, ""), "学生不得审核");
        fail(reservations.auditReservation(admin, first.getRecordId(), ReserveStatus.CANCEL, ""), "非法审核结果");
        ReserveRecord passed = ok(reservations.auditReservation(admin, first.getRecordId(), ReserveStatus.PASS, "忽略"), "审核通过");
        check(passed.getStatus() == ReserveStatus.PASS && remark.equals(passed.getRemark()), "PASS 保留原备注");
        check("OCCUPIED".equals(new ResourceDao().findById(id).get().getStatus()), "占用状态已落盘");
        check(ok(resources.queryResources(alice, ""), "审核后立即查询").isEmpty(), "共享 DAO 缓存可见");
        fail(reservations.auditReservation(admin, pending.getRecordId(), ReserveStatus.PASS, ""), "审核冲突");
        fail(reservations.submitReservation(alice, id, end, end.plusHours(1), ""), "占用资源拒绝申请");
        fail(resources.deleteResource(admin, id), "PASS 阻止删除");
        check(remark.equals(new ReserveRecordDao().findByRecordId(first.getRecordId()).get().getRemark()), "特殊字符持久化往返");
        check(ok(reservations.filterReservations(admin, id, "alice", ReserveStatus.PASS), "组合筛选").size() == 1, "三条件同时生效");
        check(ok(reservations.filterReservations(admin, id, "bob", ReserveStatus.PASS), "空筛选结果").isEmpty(), "条件不匹配为空");
        fail(reservations.filterReservations(alice, "", "", null), "学生不得筛选他人记录");
        ok(reservations.cancelReservation(alice, first.getRecordId()), "取消 PASS");
        check("AVAILABLE".equals(rd.findById(id).get().getStatus()), "取消释放资源");
        fail(reservations.cancelReservation(alice, first.getRecordId()), "不可重复取消");
        fail(reservations.auditReservation(admin, first.getRecordId(), ReserveStatus.PASS, ""), "不可审核已取消记录");
        ReserveRecord rejected = ok(reservations.auditReservation(admin, pending.getRecordId(), ReserveStatus.REJECT, "资料不足"), "驳回预约");
        // 驳回原因独立保存，不能覆盖学生提交时的备注。
        check(rejected.getStatus() == ReserveStatus.REJECT && "资料不足".equals(rejected.getRejectReason())
                && "".equals(rejected.getRemark()), "REJECT 保留申请备注和独立原因");
        check("AVAILABLE".equals(rd.findById(id).get().getStatus()), "REJECT 不改变资源状态");
        fail(reservations.cancelReservation(bob, pending.getRecordId()), "不可取消已驳回记录");
        ReserveRecord orphan = ok(reservations.submitReservation(alice, id, start, end, ""), "删除前待审核");
        ok(resources.deleteResource(admin, id), "仅非 PASS 记录允许删除");
        fail(reservations.auditReservation(admin, orphan.getRecordId(), ReserveStatus.PASS, ""), "资源删除后无法通过");
        ok(reservations.cancelReservation(alice, orphan.getRecordId()), "删除后仍可取消待审核");

        // 用可用资源加 PASS 记录触发冲突分支，保留任务 9 的手动改状态能力。
        CampusResource timing = ok(resources.addResource(admin, "时间边界", "场地", ""), "新增边界资源");
        String tid = timing.getId();
        ReserveRecord t = ok(reservations.submitReservation(alice, tid, start, end, ""), "边界原预约");
        ok(reservations.auditReservation(admin, t.getRecordId(), ReserveStatus.PASS, ""), "边界原预约通过");
        ok(resources.updateResource(admin, tid, "时间边界", "场地", "", "AVAILABLE"), "保持管理员手动改状态行为");
        fail(reservations.submitReservation(bob, tid, start.plusMinutes(30), end.plusMinutes(30), ""), "重叠 PASS 拒绝提交");
        ReserveRecord adjacent = ok(reservations.submitReservation(bob, tid, end, end.plusHours(1), ""), "首尾相接不冲突");
        ok(reservations.auditReservation(admin, adjacent.getRecordId(), ReserveStatus.PASS, ""), "相邻预约通过");
        ok(reservations.cancelReservation(alice, t.getRecordId()), "多 PASS 取消一条");
        check("OCCUPIED".equals(rd.findById(tid).get().getStatus()), "其他 PASS 存在时不释放");
        ok(reservations.cancelReservation(bob, adjacent.getRecordId()), "取消最后 PASS");
        check("AVAILABLE".equals(rd.findById(tid).get().getStatus()), "最后 PASS 取消后释放");
        ReserveRecord equal = ok(reservations.submitReservation(alice, tid, start, start, ""), "保留原有起止相等规则");
        ok(reservations.cancelReservation(alice, equal.getRecordId()), "取消 PENDING");

        // 注入失败 DAO，验证第二个文件写入失败时资源文件和缓存均回滚。
        ReserveRecord ioRecord = ok(reservations.submitReservation(alice, tid, start, end, ""), "失败测试申请");
        FailingReserveDao failing = new FailingReserveDao();
        ReserveService retry = new ReserveService(rd, failing);
        failing.failWrites = true;
        fail(retry.auditReservation(admin, ioRecord.getRecordId(), ReserveStatus.PASS, ""), "审核写入失败");
        check("AVAILABLE".equals(rd.findById(tid).get().getStatus()), "失败后缓存回滚");
        check("AVAILABLE".equals(new ResourceDao().findById(tid).get().getStatus()), "失败后文件回滚");
        check(new ReserveRecordDao().findByRecordId(ioRecord.getRecordId()).get().getStatus() == ReserveStatus.PENDING, "失败后预约不变");
        failing.failWrites = false;
        ok(retry.auditReservation(admin, ioRecord.getRecordId(), ReserveStatus.PASS, ""), "恢复后可重试审核");
        failing.failWrites = true;
        fail(retry.cancelReservation(alice, ioRecord.getRecordId()), "取消写入失败");
        check("OCCUPIED".equals(rd.findById(tid).get().getStatus()), "取消失败恢复占用缓存");
        check(new ReserveRecordDao().findByRecordId(ioRecord.getRecordId()).get().getStatus() == ReserveStatus.PASS, "取消失败保留 PASS");

        // 目录占用目标文件可稳定模拟真实写入失败，不需要改变正式目录权限。
        int before = rd.findAll().size();
        Files.move(Paths.get("resource.txt"), Paths.get("resource.backup"));
        Files.createDirectory(Paths.get("resource.txt"));
        Files.write(Paths.get("resource.txt/blocker"), new byte[] {1});
        try {
            fail(resources.addResource(admin, "写入失败", "教室", ""), "资源文件被目录占用");
            check(rd.findAll().size() == before, "失败不新增缓存资源");
        } finally {
            Files.delete(Paths.get("resource.txt/blocker"));
            Files.delete(Paths.get("resource.txt"));
            Files.move(Paths.get("resource.backup"), Paths.get("resource.txt"));
        }
        check(new ResourceDao().findAll().size() == before, "恢复原资源文件");
        check(new ReserveRecordDao().findAll().size() == failing.findAll().size(), "重新加载完整预约集合");
        System.out.println("PASS: " + checks + " checks");
    }

    /** 成功结果返回数据，断言失败立即终止。 */
    private static <T> T ok(Result<T> result, String label) {
        check(result.isSuccess(), label + "：" + result.getMsg());
        return result.getData();
    }

    /** 失败结果必须有提示且不携带成功数据。 */
    private static void fail(Result<?> result, String label) {
        check(!result.isSuccess() && result.getData() == null
                && result.getMsg() != null && !result.getMsg().isEmpty(), label);
    }

    /** 统计实际执行的断言，不依赖 JVM 的 -ea 参数。 */
    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        checks++;
    }

    /** 仅在测试中制造可控 IO 失败，生产代码仍使用原生文件 DAO。 */
    private static class FailingReserveDao extends ReserveRecordDao {
        private boolean failWrites;

        /** 加载隔离目录中真实持久化的预约。 */
        FailingReserveDao() throws IOException {
            super();
        }

        /** 覆盖写入边界以测试补偿与恢复。 */
        @Override
        public void saveAll(List<ReserveRecord> records) throws IOException {
            if (failWrites) {
                throw new IOException("测试注入写入失败");
            }
            super.saveAll(records);
        }
    }
}
