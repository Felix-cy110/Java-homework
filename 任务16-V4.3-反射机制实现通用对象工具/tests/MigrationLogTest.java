import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.entity.OperationLogRecord;
import java.util.List;

/** 仅在迁移隔离副本运行，检查原日志载入及登录产生的新日志可跨 JVM 保留。 */
public class MigrationLogTest {
    /** 参数为运行前预期日志数；不显示实际账号或密码。 */
    public static void main(String[] args) throws Exception {
        int expected = Integer.parseInt(args[0]);
        require(DaoFactory.getOperationLogDao().findAll().isSuccess(), "日志文件加载");
        List<OperationLogRecord> before = DaoFactory.getOperationLogDao().findAll().getData();
        require(before.size() == expected, "重启前日志数量");
        int accounts = DaoFactory.getUserDao().findAll().size();
        MigrationStatisticsTest.main(new String[0]);
        List<OperationLogRecord> after = DaoFactory.getOperationLogDao().findAll().getData();
        require(after.size() == expected + accounts, "每个既有账号登录产生一条日志");
        for (int i = 0; i < before.size(); i++) {
            require(before.get(i).getLogId().equals(after.get(i).getLogId()), "原日志身份保留");
        }
        System.out.println("PASS migrated log restart: before=" + expected + ", after=" + after.size());
    }
    /** 失败只给出检查名称，不输出真实业务字段。 */
    private static void require(boolean value, String name) {
        if (!value) { throw new AssertionError(name); }
    }
}
