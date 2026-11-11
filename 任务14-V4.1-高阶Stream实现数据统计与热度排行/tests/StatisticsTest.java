import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.dao.ResourceDao;
import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.entity.*;
import com.campus.reservation.service.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 独立临时数据上的报表测试：覆盖统计口径、边界、权限与实时变化。 */
public class StatisticsTest {
    private static int checks;
    private static final LocalDateTime TIME = LocalDateTime.of(2030, 1, 2, 10, 0);

    /** 固定样本验证手工期望值，再通过原业务操作验证实时报表。 */
    public static void main(String[] args) throws Exception {
        UserService users = UserService.getInstance();
        require(users.initializeDefaultAdministrator().isSuccess(), "初始化管理员");
        User admin = users.loginUser("admin", "123456").getData();
        User alice = users.registerUser("stat_alice", "123456").getData();
        User bob = users.registerUser("stat_bob", "123456").getData();
        ReserveRecordService service = ReserveRecordService.getInstance();
        ReserveController controller = new ReserveController();
        ResourceDao resources = DaoFactory.getResourceDao();
        ReserveRecordDao records = DaoFactory.getReserveRecordDao();

        require(service.queryResourceStatistics(admin).getData().isEmpty(), "空资源统计");
        require(service.queryHotResources(admin, 10).getData().isEmpty(), "空排行榜");
        require(service.queryUserStatistics(admin).getData().isEmpty(), "空用户统计");
        List<StatusStatDTO> empty = controller.queryStatusStatistics(admin).getData();
        require(empty.size() == 4, "空集合仍显示四种状态");
        require(empty.stream().allMatch(row -> row.getCount() == 0), "空状态均为零");
        require(new ResourceStatDTO().getPassRate() == 0.0, "空DTO比率为零");

        // 学生、空身份、无效角色在服务和控制器两层入口都无法读取全局数据。
        for (User denied : Arrays.asList(alice, null, new User("unknown", "", "guest", TIME))) {
            require(!service.queryResourceStatistics(denied).isSuccess(), "服务资源报表越权");
            require(!service.queryHotResources(denied, 5).isSuccess(), "服务热度报表越权");
            require(!service.queryUserStatistics(denied).isSuccess(), "服务用户报表越权");
            require(!service.queryStatusStatistics(denied).isSuccess(), "服务状态报表越权");
            require(!controller.queryResourceStatistics(denied).isSuccess(), "控制器资源报表越权");
            require(!controller.queryHotResources(denied, 5).isSuccess(), "控制器热度报表越权");
            require(!controller.queryUserStatistics(denied).isSuccess(), "控制器用户报表越权");
            require(!controller.queryStatusStatistics(denied).isSuccess(), "控制器状态报表越权");
        }
        require(!controller.queryHotResources(admin, 0).isSuccess(), "零条数被拒绝");
        require(!controller.queryHotResources(admin, -1).isSuccess(), "负条数被拒绝");
        require(!controller.queryHotResources(admin, Integer.MIN_VALUE).isSuccess(), "最小整数被拒绝");

        resources.saveAll(Arrays.asList(resource("a", "A教室", "OCCUPIED"),
                resource("b", "B教室", "OCCUPIED"), resource("c", "C教室", "AVAILABLE"),
                resource("unused", "未被预约", "AVAILABLE")));
        records.saveAll(Arrays.asList(
                record("a1", "a", alice, ReserveStatus.PASS), record("a2", "a", bob, ReserveStatus.PASS),
                record("a3", "a", alice, ReserveStatus.REJECT), record("a4", "a", bob, ReserveStatus.CANCEL),
                record("a5", "a", alice, ReserveStatus.PENDING),
                record("b1", "b", alice, ReserveStatus.PASS), record("b2", "b", alice, ReserveStatus.PASS),
                record("b3", "b", bob, ReserveStatus.REJECT), record("b4", "b", alice, ReserveStatus.CANCEL),
                record("c1", "c", bob, ReserveStatus.CANCEL), record("c2", "c", bob, ReserveStatus.PENDING),
                record("g1", "gone", bob, ReserveStatus.REJECT)));
        byte[] userBefore = Files.readAllBytes(Paths.get("user.txt"));
        byte[] resourceBefore = Files.readAllBytes(Paths.get("resource.txt"));
        byte[] reserveBefore = Files.readAllBytes(Paths.get("reserve.txt"));
        List<ResourceStatDTO> report = controller.queryResourceStatistics(admin).getData();
        require(report.size() == 4, "四个有记录分组包含已删除资源");
        ResourceStatDTO a = report.get(0);
        require("a".equals(a.getResourceId()) && "A教室".equals(a.getResourceName()), "资源信息");
        require(a.getTotalApplyCount() == 5 && a.getPassCount() == 2
                && a.getRejectCount() == 1 && a.getCancelCount() == 1, "A资源全部状态总数");
        require(Math.abs(a.getPassRate() - 0.4) < 1e-12, "通过率使用浮点除法");
        require(report.get(1).getPassRate() == 0.5, "B资源通过率");
        require(report.get(2).getPassRate() == 0.0, "有申请无通过");
        require("资源已删除".equals(report.get(3).getResourceName()), "删除资源仍保留历史分组");
        require(report.stream().noneMatch(row -> "unused".equals(row.getResourceId())), "无预约资源不产生分组");
        List<ResourceStatDTO> top = controller.queryHotResources(admin, 1).getData();
        require(top.size() == 1 && "a".equals(top.get(0).getResourceId()), "Top1同热度按ID稳定排序");
        require(controller.queryHotResources(admin, 2).getData().stream().map(ResourceStatDTO::getResourceId)
                .collect(Collectors.toList()).equals(Arrays.asList("a", "b")), "Top2通过数量排序");
        require(controller.queryHotResources(admin, Integer.MAX_VALUE).getData().size() == 4, "超出总数安全截断");
        require(controller.queryHotResources(admin, 4).getData().get(3).getPassCount() == 0, "零热度保留");

        List<UserStatDTO> people = controller.queryUserStatistics(admin).getData();
        require(people.size() == 2, "两个有预约用户");
        require(people.get(0).getApplicantUsername().equals(alice.getUsername()), "用户排序");
        require(people.get(0).getTotalApplyCount() == 6 && people.get(0).getPassCount() == 3
                && people.get(0).getRejectCount() == 1, "Alice全部状态汇总");
        require(people.get(1).getTotalApplyCount() == 6 && people.get(1).getPassCount() == 1
                && people.get(1).getRejectCount() == 2, "Bob包含删除资源记录");
        List<StatusStatDTO> status = controller.queryStatusStatistics(admin).getData();
        require(status.stream().map(StatusStatDTO::getStatus).collect(Collectors.toList())
                .equals(Arrays.asList(ReserveStatus.values())), "状态输出顺序");
        require(status.stream().map(StatusStatDTO::getCount).collect(Collectors.toList())
                .equals(Arrays.asList(2L, 4L, 3L, 3L)), "四种状态计数");
        require(status.stream().mapToLong(StatusStatDTO::getCount).sum() == 12, "全局状态总数一致");
        require(report.stream().mapToLong(ResourceStatDTO::getTotalApplyCount).sum() == 12, "资源总数一致");
        require(people.stream().mapToLong(UserStatDTO::getTotalApplyCount).sum() == 12, "用户总数一致");
        a.setPassCount(999);
        a.setResourceName("只改DTO");
        require(controller.queryResourceStatistics(admin).getData().get(0).getPassCount() == 2, "DTO与下次统计隔离");
        require(resources.findById("a").get().getName().equals("A教室"), "DTO与资源实体隔离");
        require(Arrays.equals(userBefore, Files.readAllBytes(Paths.get("user.txt"))), "统计不写用户文件");
        require(Arrays.equals(resourceBefore, Files.readAllBytes(Paths.get("resource.txt"))), "统计不写资源文件");
        require(Arrays.equals(reserveBefore, Files.readAllBytes(Paths.get("reserve.txt"))), "统计不写预约文件");

        // 清空的只是本进程隔离样本，之后通过原服务真实提交、审核、取消和删除。
        records.saveAll(Collections.emptyList());
        resources.saveAll(Collections.singletonList(resource("live", "实时教室", "AVAILABLE")));
        require(controller.queryResourceStatistics(admin).getData().isEmpty(), "重查不缓存旧报表");
        ReserveRecord live = controller.submitReservation(alice, "live", TIME, TIME.plusHours(1), "实时").getData();
        require(live != null, "原业务提交成功");
        require(controller.queryResourceStatistics(admin).getData().get(0).getTotalApplyCount() == 1, "提交后实时总数");
        require(controller.queryStatusStatistics(admin).getData().get(0).getCount() == 1, "提交后待审统计");
        require(controller.auditReservation(admin, live.getRecordId(), ReserveStatus.PASS, "").isSuccess(), "原业务通过");
        require(controller.queryHotResources(admin, 1).getData().get(0).getPassRate() == 1.0, "审核后实时排行");
        require(controller.cancelReservation(alice, live.getRecordId()).isSuccess(), "原业务取消");
        ResourceStatDTO cancelled = controller.queryResourceStatistics(admin).getData().get(0);
        require(cancelled.getCancelCount() == 1 && cancelled.getPassCount() == 0 && cancelled.getPassRate() == 0,
                "通过后取消按当前状态计数");
        require(ResourceService.getInstance().deleteResource(admin, "live").isSuccess(), "原业务删除已释放资源");
        require("资源已删除".equals(controller.queryResourceStatistics(admin).getData().get(0).getResourceName()),
                "真实删除后报表回退名称");
        require(controller.queryUserStatistics(admin).getData().get(0).getTotalApplyCount() == 1, "实时用户报表");
        require(controller.queryStatusStatistics(admin).getData().get(3).getCount() == 1, "实时状态报表");
        System.out.println("PASS statistics checks=" + checks);
    }

    /** 建立明确的演示资源，固定 ID 便于核对排序。 */
    private static CampusResource resource(String id, String name, String status) {
        return new CampusResource(id, name, "教室", status, "测试楼");
    }

    /** 历史样本覆盖各状态；只写临时数据，不影响正式数据。 */
    private static ReserveRecord record(String id, String resourceId, User user, ReserveStatus status) {
        return new ReserveRecord(id, resourceId, user.getUsername(), TIME.minusDays(1), TIME, TIME.plusHours(1), status, "统计测试");
    }

    /** 断言失败仅输出用例名，不输出账号凭据。 */
    private static void require(boolean condition, String name) {
        if (!condition) { throw new AssertionError(name); }
        checks++;
    }
}
