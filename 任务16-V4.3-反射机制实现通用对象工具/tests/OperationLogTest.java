import com.campus.reservation.common.*;
import com.campus.reservation.common.annotation.OperationLog;
import com.campus.reservation.controller.*;
import com.campus.reservation.dao.*;
import com.campus.reservation.entity.*;
import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.service.*;
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/** 隔离目录中的注解、切面、权限、持久化与全部核心操作集成测试。 */
public class OperationLogTest {
    private static int checks;
    private static final OperationLogDao DAO = DaoFactory.getOperationLogDao();

    /** 小型业务契约用来验证切面能从接口方法定位到实现注解。 */
    public interface SampleContract { Result<String> same(String text); }
    /** 只用于测试，不参与正式服务；异常对象复用便于检查原样传播。 */
    public static class Sample implements SampleContract {
        final Result<String> output = Result.success("原结果", "原数据");
        final RuntimeException failure = new IllegalStateException("敏感示例 Secret-Once-45");
        final Exception checked = new Exception("受检异常");
        int calls;
        /** 返回同一结果，验证切面不能包装或重复调用。 */
        @OperationLog("测试原结果")
        public Result<String> same(String text) { calls++; return output; }
        /** 无注解查询不产生日志。 */
        public Result<String> plain() { calls++; return output; }
        /** 抛出的运行时异常必须仍是原对象，落盘消息需脱敏。 */
        @OperationLog(value = "测试异常", sensitiveParams = {0})
        public Result<String> fail(String secret) { calls++; throw failure; }
        /** 受检异常也不能被反射包装替换。 */
        @OperationLog("测试受检异常")
        public Result<String> checked() throws Exception { throw checked; }
    }

    /** 从空工作目录跑全部日志路径，已有业务由继承测试单独回归。 */
    public static void main(String[] args) throws Throwable {
        check(OperationLog.class.getAnnotation(Retention.class).value() == RetentionPolicy.RUNTIME, "运行时保留");
        check(Arrays.equals(OperationLog.class.getAnnotation(Target.class).value(), new ElementType[] {ElementType.METHOD}), "仅方法");
        check(DAO == OperationLogDao.getInstance() && DAO == DaoFactory.getOperationLogDao(), "DAO单例");
        check(OperationLogServiceImpl.getInstance() == OperationLogServiceImpl.getInstance(), "查询单例");
        check(rows().isEmpty(), "初始为空");
        UserController users = new UserController();
        ResourceController resources = new ResourceController();
        ReserveController reserves = new ReserveController();
        OperationLogController logs = new OperationLogController();
        check(users.initializeDefaultAdministrator().isSuccess(), "默认管理员");
        check(rows().isEmpty(), "无注解初始化不记录");
        User admin = users.loginUser("admin", "123456").getData();
        check(admin != null, "登录成功");
        OperationLogRecord first = last();
        check("admin".equals(first.getUsername()), "登录账号");
        check(first.getMethodFullName().endsWith("UserServiceImpl.loginUser"), "实现类全限定方法");
        check("用户登录".equals(first.getOperationDesc()) && !first.getParams().contains("123456"), "描述和参数脱敏");
        check(first.getResultInfo().contains("success=true") && !first.getResultInfo().contains("123456"), "结果信息脱敏");
        check(first.getExceptionMsg().isEmpty(), "成功无异常");
        check(!users.loginUser("admin", "Wrong-Secret-32").isSuccess(), "失败登录");
        check(last().getResultInfo().contains("success=false") && last().getExceptionMsg().isEmpty(), "失败Result不是虚构异常");
        User student = users.registerUser("log_student", "Secret-Student-67").getData();
        check(student != null && "log_student".equals(last().getUsername()), "注册账号来源");
        check(!users.registerUser("log_student", "Secret-Student-67").isSuccess(), "重复注册照常失败");
        check(!resources.addResource(student,"拒绝","教室","A").isSuccess(), "学生写入被拒绝");
        check("log_student".equals(last().getUsername()) && last().getResultInfo().contains("false"), "拒绝结果也记录");
        CampusResource room = resources.addResource(admin,"日志教室","教室","A101").getData();
        check(room != null, "新增资源");
        check(resources.updateResource(admin,room.getId(),"日志教室","教室","A102","AVAILABLE").isSuccess(), "修改资源");
        LocalDateTime start=LocalDateTime.now().plusDays(5);
        ReserveRecord record=reserves.submitReservation(student,room.getId(),start,start.plusHours(1),"日志备注").getData();
        check(record!=null, "提交预约");
        check(reserves.auditReservation(admin,record.getRecordId(),ReserveStatus.PASS,"").isSuccess(), "审核通过");
        check(last().getParams().contains("PASS"), "通过方向记录");
        check(reserves.cancelReservation(student,record.getRecordId()).isSuccess(), "取消已通过");
        ReserveRecord reject=reserves.submitReservation(student,room.getId(),start.plusDays(1),start.plusDays(1).plusHours(1),"日志备注").getData();
        check(reserves.auditReservation(admin,reject.getRecordId(),ReserveStatus.REJECT,"安排冲突").isSuccess(), "审核驳回");
        check(last().getParams().contains("REJECT") && last().getParams().contains("安排冲突"), "驳回方向和原因");
        check(resources.deleteResource(admin,room.getId()).isSuccess(), "删除资源");
        int before=rows().size();
        reserves.queryResourceStatistics(admin);reserves.queryHotResources(admin,5);
        reserves.queryUserStatistics(admin);reserves.queryStatusStatistics(admin);
        resources.queryResources(admin,"");users.findUserByUsername("admin");
        check(rows().size()==before, "所有只读查询不记录");
        check(logs.queryAllLogs(admin).isSuccess(), "管理员全部查询");
        check(!logs.queryAllLogs(student).isSuccess() && !logs.queryAllLogs(null).isSuccess(), "学生和空身份被拒绝");
        check(!logs.queryAllLogs(new User("x","demo","invalid",LocalDateTime.now())).isSuccess(), "无效角色被拒绝");
        check(!logs.queryLogsByUsername(student,"admin").isSuccess(), "筛选也鉴权");
        check(!logs.queryLogsByUsername(admin," ").isSuccess(), "空筛选拒绝");
        check(logs.queryLogsByUsername(admin,"not_found").getData().isEmpty(), "无匹配返回空列表");
        List<OperationLogRecord> mine=logs.queryLogsByUsername(admin,"log_student").getData();
        check(!mine.isEmpty() && mine.stream().allMatch(r -> "log_student".equals(r.getUsername())), "用户名精确匹配");
        check(rows().size()==before, "日志查询不递归记录");
        List<OperationLogRecord> ordered=logs.queryAllLogs(admin).getData();
        for(int i=1;i<ordered.size();i++)check(!ordered.get(i).getOperateTime().isAfter(ordered.get(i-1).getOperateTime()), "倒序");
        Sample sample=new Sample();
        Method method=SampleContract.class.getMethod("same",String.class);
        Result<String> same=LogAspect.invoke(sample,method,new Object[]{"输入\t换行\n\\back"},admin);
        check(same==sample.output && sample.calls==1,"原引用且只执行一次");
        check(last().getParams().contains("\t") && last().getParams().contains("\n"),"内存保留原字段");
        int number=rows().size();
        LogAspect.invoke(sample,Sample.class.getMethod("plain"),null,admin);
        check(rows().size()==number,"无注解不记录");
        try { LogAspect.invoke(sample,Sample.class.getMethod("fail",String.class),new Object[]{"Secret-Once-45"},admin);throw new AssertionError("应抛异常"); }
        catch(RuntimeException e){check(e==sample.failure,"运行时异常对象原样传播");}
        check(last().getExceptionMsg().contains("IllegalStateException") && last().getExceptionMsg().contains("OperationLogTest$Sample"),"记录异常类型与堆栈");
        check(!last().getExceptionMsg().contains("Secret-Once-45"),"堆栈密码脱敏");
        try { LogAspect.invoke(sample,Sample.class.getMethod("checked"),new Object[0],null);throw new AssertionError("应抛异常"); }
        catch(Exception e){check(e==sample.checked,"受检异常对象原样传播");}
        check("未登录".equals(last().getUsername()),"空身份标签");
        OperationLogRecord mutable=last();String id=mutable.getLogId();mutable.setUsername("篡改");
        check(!"篡改".equals(last().getUsername()),"查询深拷贝");
        check(!DAO.save(last()).isSuccess(),"重复日志拒绝");
        OperationLogRecord tail=new OperationLogRecord(UUID.randomUUID().toString(),"roundtrip",LocalDateTime.now(),"test.roundtrip","转义", "\t\r\n\\\uFEFF", "ok", "");
        check(DAO.save(tail).isSuccess(),"八字段转义保存");tail.setUsername("篡改");
        check("roundtrip".equals(last().getUsername()),"保存深拷贝");
        Set<String> ids=new HashSet<>();for(OperationLogRecord row:rows())check(ids.add(row.getLogId()),"ID唯一");
        String disk=new String(Files.readAllBytes(Paths.get("operation_log.txt")),StandardCharsets.UTF_8);
        check(!disk.contains("123456")&&!disk.contains("Secret-Student-67")&&!disk.contains("Wrong-Secret-32")&&!disk.contains("Secret-Once-45"),"日志不含密码");
        for(String line:disk.split("\n"))check(line.split("\t",-1).length==8,"八列格式");
        check(!last().toString().contains("\n")&&!last().toString().contains("\t"),"终端显示不注入换行");
        System.out.println("PASS OperationLogTest: "+checks+" checks; logs="+rows().size());
    }
    /** 查询失败不能被测试误判为空集合。 */
    private static List<OperationLogRecord> rows(){Result<List<OperationLogRecord>> r=DAO.findAll();if(!r.isSuccess())throw new AssertionError(r.getMsg());return r.getData();}
    /** 返回最新插入记录，不依赖排序时间精度。 */
    private static OperationLogRecord last(){List<OperationLogRecord> r=rows();return r.get(r.size()-1);}
    /** 只打印断言名，不在失败信息输出账号口令。 */
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
}
