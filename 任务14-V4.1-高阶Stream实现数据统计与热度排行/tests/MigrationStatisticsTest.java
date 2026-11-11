import com.campus.reservation.common.Result;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.entity.*;
import java.util.List;

/** 只读校验迁移副本的原业务以及统计总数，不输出账号密码。 */
public class MigrationStatisticsTest {
    /** 每次独立 JVM 检查加载、登录、查询、关联和四类报表。 */
    public static void main(String[] args) throws Exception {
        SingletonTest.main(new String[] {"migration"});
        User admin = DaoFactory.getUserDao().findAll().stream()
                .filter(user -> "admin".equals(user.getRole())).findFirst()
                .orElseThrow(() -> new AssertionError("迁移数据缺少管理员，未自行补造账号"));
        ReserveController controller = new ReserveController();
        long total = DaoFactory.getReserveRecordDao().findAll().size();
        Result<List<ResourceStatDTO>> resources = controller.queryResourceStatistics(admin);
        Result<List<UserStatDTO>> users = controller.queryUserStatistics(admin);
        Result<List<StatusStatDTO>> states = controller.queryStatusStatistics(admin);
        Result<List<ResourceStatDTO>> top = controller.queryHotResources(admin, 10);
        require(resources.isSuccess() && users.isSuccess() && states.isSuccess() && top.isSuccess());
        require(resources.getData().stream().mapToLong(ResourceStatDTO::getTotalApplyCount).sum() == total);
        require(users.getData().stream().mapToLong(UserStatDTO::getTotalApplyCount).sum() == total);
        require(states.getData().stream().mapToLong(StatusStatDTO::getCount).sum() == total);
        require(states.getData().size() == 4);
        require(top.getData().size() == Math.min(10, resources.getData().size()));
        System.out.println("PASS migrated report checks=6");
    }

    /** 失败仅给出统计校验错误，不输出实际业务记录。 */
    private static void require(boolean condition) {
        if (!condition) { throw new AssertionError("迁移报表总数或状态校验失败"); }
    }
}
