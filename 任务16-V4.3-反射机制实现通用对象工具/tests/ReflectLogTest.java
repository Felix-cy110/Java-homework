import com.campus.reservation.common.*;
import com.campus.reservation.common.annotation.OperationLog;
import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.entity.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** 证明切面能处理新 Bean，记录完整 Result，且失败降级不泄露密码、不替换业务结果。 */
public class ReflectLogTest {
    private static int checks;
    /** 新实体没有自定义 toString，工具仍应自动识别属性。 */
    public static class Payload {
        public String getTitle() { return "reflection-only-field"; }
        public User getOwner() { return new User("owner", "Nested-Password-99", "student", null); }
    }
    /** 模拟坏 getter/toString：日志必须回退安全身份信息而非默认明文输出。 */
    public static class UnsafePayload {
        public String getValue() { throw new IllegalStateException("getter-sensitive"); }
        public String getPassword() { throw new AssertionError("敏感getter不允许被调用"); }
        @Override public String toString() { return "Fallback-Password-99"; }
    }
    /** 私有测试业务仅返回同一个对象以验证透明性。 */
    public static class Service {
        private int calls;
        @OperationLog("反射集成")
        public Result<Object> call(Object value) { calls++;return Result.success("原提示",value); }
    }
    /** 每次运行使用独立空目录，不读取真实账号。 */
    public static void main(String[] args) throws Throwable {
        Service service=new Service();Payload payload=new Payload();User empty=new User();
        Result<Object> result=LogAspect.invoke(service,Service.class.getMethod("call",Object.class),new Object[]{payload},empty);
        check(result.getData()==payload && service.calls==1, "业务对象和执行次数不变");
        OperationLogRecord row=last();
        check("未登录".equals(row.getUsername()), "空User由通用工具识别");
        check(row.getParams().contains("reflection-only-field") && row.getResultInfo().contains("reflection-only-field"), "新Bean自动序列化");
        check(row.getResultInfo().contains("success=true") && row.getResultInfo().contains("msg=原提示"), "完整Result getter");
        check(!row.getParams().contains("Nested-Password-99")&&!row.getResultInfo().contains("Nested-Password-99"), "嵌套User密码脱敏");
        UnsafePayload unsafe=new UnsafePayload();
        Result<Object> degraded=LogAspect.invoke(service,Service.class.getMethod("call",Object.class),new Object[]{unsafe},null);
        check(degraded.getData()==unsafe && service.calls==2, "读取失败不影响业务");
        row=last();check(row.getParams().contains("UnsafePayload@") && row.getResultInfo().contains("UnsafePayload@"), "敏感场景安全降级");
        String disk=new String(Files.readAllBytes(Paths.get("operation_log.txt")),StandardCharsets.UTF_8);
        check(!disk.contains("Nested-Password-99")&&!disk.contains("Fallback-Password-99")&&!disk.contains("getter-sensitive"), "落盘没有泄露");
        check(empty.getUsername()==null && empty.getPassword()==null, "不修改输入对象");
        System.out.println("PASS ReflectLogTest: "+checks+" checks");
    }
    /** 日志DAO快照的最后一条为本次调用产生的记录。 */
    private static OperationLogRecord last() {
        List<OperationLogRecord> logs=DaoFactory.getOperationLogDao().findAll().getData();return logs.get(logs.size()-1);
    }
    /** 统一断言计数。 */
    private static void check(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
}
