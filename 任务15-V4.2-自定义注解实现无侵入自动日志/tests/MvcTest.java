import com.campus.reservation.common.Result;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.controller.*;
import com.campus.reservation.entity.*;
import com.campus.reservation.common.BusinessException;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/** 独立临时目录中的控制器集成测试，验证越权请求不能绕过业务层。 */
public class MvcTest {
    private static int checks;

    /** 覆盖控制器调用链、校验失败、状态流转与异常包装。 */
    public static void main(String[] args) throws Exception {
        UserController users = new UserController();
        ResourceController resources = new ResourceController();
        ReserveController reservations = new ReserveController();
        require(users.initializeDefaultAdministrator().isSuccess(), "初始化");
        require(!users.checkUsername(null).isSuccess(), "空账号校验");
        require(!users.loginUser("admin", "wrong").isSuccess(), "错误密码");
        User admin = users.loginUser("admin", "123456").getData();
        Result<User> registration = users.registerUser("mvc_student", "123456");
        require(registration.isSuccess(), "学生注册");
        User student = registration.getData();
        require("student".equals(student.getRole()), "注册角色");
        require(users.findUserByUsername(student.getUsername()).isSuccess(), "账号查询");
        require(!users.registerUser("mvc_student", "123456").isSuccess(), "重复账号");
        require(!resources.addResource(student, "教室", "教室", "A101").isSuccess(), "禁止学生新增");
        CampusResource resource = resources.addResource(admin, "MVC教室", "教室", "A101").getData();
        require(resource != null, "管理员新增");
        require(resources.updateResource(admin, resource.getId(), "MVC教室", "教室", "A102", "AVAILABLE").isSuccess(), "资源修改");
        require(resources.queryResources(student, "MVC").getData().size() == 1, "资源查询");
        require(!resources.deleteResource(student, resource.getId()).isSuccess(), "禁止学生删除");
        require(!reservations.queryAllReservations(student).isSuccess(), "禁止学生查看全部");
        require(!reservations.queryPendingReservations(student).isSuccess(), "禁止学生查看待审");
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        ReserveRecord record = reservations.submitReservation(student, resource.getId(), start, start.plusHours(1), "测试").getData();
        require(record != null && record.getStatus() == ReserveStatus.PENDING, "申请待审");
        require(reservations.queryMyReservations(student).getData().size() == 1, "我的预约");
        require(reservations.queryPendingReservations(admin).getData().size() == 1, "待审查询");
        require(reservations.filterReservations(admin, resource.getId(), student.getUsername(), ReserveStatus.PENDING).getData().size() == 1, "组合筛选");
        require(!reservations.auditReservation(student, record.getRecordId(), ReserveStatus.PASS, "").isSuccess(), "禁止学生审核");
        require(reservations.auditReservation(admin, record.getRecordId(), ReserveStatus.PASS, "").isSuccess(), "审核通过");
        require(!resources.deleteResource(admin, resource.getId()).isSuccess(), "通过预约阻止删除");
        require(reservations.cancelReservation(student, record.getRecordId()).isSuccess(), "学生取消");
        require(!reservations.cancelReservation(student, record.getRecordId()).isSuccess(), "重复取消");
        require(reservations.queryAllReservations(admin).getData().get(0).getStatus() == ReserveStatus.CANCEL, "取消状态");
        require(resources.deleteResource(admin, resource.getId()).isSuccess(), "取消后可删除");

        // 异常边界单独验证，防止服务内部已包装异常掩盖控制器捕获失效。
        Class<?> support = UserController.class.getSuperclass();
        Method execute = support.getDeclaredMethod("execute", Supplier.class);
        execute.setAccessible(true);
        Supplier<Result<Void>> failure = () -> { throw new BusinessException("测试业务异常"); };
        Result<?> result = (Result<?>) execute.invoke(users, failure);
        require(!result.isSuccess() && "测试业务异常".equals(result.getMsg()), "控制器包装异常");
        System.out.println("PASS MVC controller checks=" + checks);
    }

    /** 失败信息只包含用例名称，不包含业务凭据。 */
    private static void require(boolean condition, String name) {
        if (!condition) { throw new AssertionError(name); }
        checks++;
    }
}
