import com.campus.reservation.common.*;
import com.campus.reservation.common.annotation.OperationLog;
import com.campus.reservation.controller.*;
import com.campus.reservation.dao.*;
import com.campus.reservation.entity.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/** 日志加载/写入故障隔离验证，每个模式必须使用独立 JVM 与工作目录。 */
public class LogFailureTest {
    /** 用原对象验证日志失败不会掩盖已有业务异常。 */
    public static class Throwing {
        final RuntimeException error=new IllegalStateException("原业务异常");
        /** 注解异常路径也应尽力记录，最终仍抛原异常。 */
        @OperationLog("故障隔离异常")
        public Result<Void> run(){throw error;}
    }
    /** 模式覆盖文件变目录、损坏、重复ID和末行无换行。 */
    public static void main(String[] args)throws Throwable{
        String mode=args[0];Path path=Paths.get("operation_log.txt");
        if("malformed".equals(mode))Files.write(path,"invalid\n".getBytes(StandardCharsets.UTF_8));
        if("directory".equals(mode))Files.createDirectory(path);
        if("duplicate".equals(mode))Files.write(path,("same\tu\t2026-01-01T10:00\ttest.m\td\tp\tr\t\n"+
                "same\tu\t2026-01-01T10:00\ttest.m\td\tp\tr\t\n").getBytes(StandardCharsets.UTF_8));
        if("nonewline".equals(mode))Files.write(path,"old\tu\t2026-01-01T10:00\ttest.m\td\tp\tr\t".getBytes(StandardCharsets.UTF_8));
        OperationLogDao dao=DaoFactory.getOperationLogDao();
        byte[] before=Files.isRegularFile(path)?Files.readAllBytes(path):null;
        if("write".equals(mode)){Files.move(path,Paths.get("old-log.txt"));Files.createDirectory(path);}
        UserController users=new UserController();users.initializeDefaultAdministrator();
        Result<User> login=users.loginUser("admin","123456");
        if(!login.isSuccess())throw new AssertionError("日志故障影响登录");
        if("nonewline".equals(mode)){
            if(!dao.findAll().isSuccess()||dao.findAll().getData().size()!=2)throw new AssertionError("无末尾换行追加");
            if(Files.readAllLines(path,StandardCharsets.UTF_8).size()!=2)throw new AssertionError("日志行粘连");
        }else{
            if(new OperationLogController().queryAllLogs(login.getData()).isSuccess())throw new AssertionError("故障被静默隐藏");
            CampusResource created=new ResourceController().addResource(login.getData(),"日志故障仍保存资源","教室","A101").getData();
            if(created==null||!Files.exists(Paths.get("resource.txt")))throw new AssertionError("日志故障影响业务提交");
            if(before!=null&&!"write".equals(mode)&&!Arrays.equals(before,Files.readAllBytes(path)))throw new AssertionError("破坏损坏文件");
            Throwing sample=new Throwing();
            try{LogAspect.invoke(sample,Throwing.class.getMethod("run"),null,login.getData());throw new AssertionError("应抛异常");}
            catch(RuntimeException e){if(e!=sample.error)throw new AssertionError("日志掩盖原异常");}
        }
        System.out.println("PASS LogFailureTest: "+mode);
    }
}
