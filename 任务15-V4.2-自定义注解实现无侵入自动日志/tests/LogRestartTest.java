import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.OperationLogRecord;
import java.util.List;

/** 在新的 JVM 中验证历史日志加载，包含特殊字符和异常堆栈。 */
public class LogRestartTest {
    /** 不执行新业务；检查数量、转义与历史异常均未丢失。 */
    public static void main(String[] args) {
        Result<List<OperationLogRecord>> loaded=DaoFactory.getOperationLogDao().findAll();
        if(!loaded.isSuccess())throw new AssertionError("重启加载失败");
        List<OperationLogRecord> rows=loaded.getData();
        if(rows.size()<15)throw new AssertionError("历史数量丢失");
        if(rows.stream().noneMatch(r -> "\t\r\n\\\uFEFF".equals(r.getParams())))throw new AssertionError("转义往返");
        if(rows.stream().noneMatch(r -> r.getExceptionMsg().contains("OperationLogTest$Sample")))throw new AssertionError("历史堆栈");
        System.out.println("PASS LogRestartTest: 4 checks; logs="+rows.size());
    }
}
