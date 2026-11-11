import com.campus.reservation.enums.ReserveStatus;
import com.campus.reservation.dao.*;
import com.campus.reservation.entity.*;
import com.campus.reservation.service.*;
import com.campus.reservation.controller.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 在独立工作目录中验证实例、缓存和迁移数据。 */
public class SingletonTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "migration".equals(args[0])) {
            verifyMigration();
            return;
        }
        for (Class<?> type : new Class<?>[] {UserDao.class, ResourceDao.class,
                ReserveRecordDao.class, UserService.class, ResourceService.class, ReserveRecordService.class}) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                require(Modifier.isPrivate(constructor.getModifiers()), "构造器必须私有");
            }
        }
        UserDao users = DaoFactory.getUserDao();
        ResourceDao resources = DaoFactory.getResourceDao();
        ReserveRecordDao records = DaoFactory.getReserveRecordDao();
        require(users == DaoFactory.getUserDao() && users == UserDao.getInstance(), "用户 DAO 身份");
        require(resources == DaoFactory.getResourceDao() && resources == ResourceDao.getInstance(), "资源 DAO 身份");
        require(records == DaoFactory.getReserveRecordDao() && records == ReserveRecordDao.getInstance(), "预约 DAO 身份");
        require(UserService.getInstance() == UserService.getInstance(), "用户服务身份");
        require(ResourceService.getInstance() == ResourceService.getInstance(), "资源服务身份");
        require(ReserveRecordService.getInstance() == ReserveRecordService.getInstance(), "预约服务身份");

        int beforeUsers = users.findAll().size();
        int beforeResources = resources.findAll().size();
        int beforeRecords = records.findAll().size();
        // 外部修改副本后再次取实例，证明当前进程不会再次读取文件。
        Files.write(Paths.get("user.txt"), "invalid\n".getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get("resource.txt"), "invalid\n".getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get("reserve.txt"), "invalid\n".getBytes(StandardCharsets.UTF_8));
        require(DaoFactory.getUserDao().findAll().size() == beforeUsers, "用户只加载一次");
        require(DaoFactory.getResourceDao().findAll().size() == beforeResources, "资源只加载一次");
        require(DaoFactory.getReserveRecordDao().findAll().size() == beforeRecords, "预约只加载一次");
        System.out.println("PASS singleton checks=" + checks);
    }

    /** 不回显账号或密码，核对迁移副本的登录、查询与关联。 */
    private static void verifyMigration() throws Exception {
        UserDao users = DaoFactory.getUserDao();
        ResourceDao resources = DaoFactory.getResourceDao();
        ReserveRecordDao records = DaoFactory.getReserveRecordDao();
        // 迁移验证走本阶段控制器，兼查新调用链与原数据兼容。
        UserController userController = new UserController();
        ResourceController resourceController = new ResourceController();
        ReserveController reserveController = new ReserveController();
        for (User user : users.findAll()) {
            require(userController.loginUser(user.getUsername(), user.getPassword()).isSuccess(), "登录");
            require(resourceController.queryResources(user, "").isSuccess(), "资源查询");
            require(("admin".equals(user.getRole())
                    ? reserveController.queryAllReservations(user)
                    : reserveController.queryMyReservations(user)).isSuccess(), "预约查询");
        }
        for (ReserveRecord record : records.findAll()) {
            require(users.findByUsername(record.getApplicantUsername()).isPresent(), "预约账号关联");
            // 非 PASS 记录允许保留已删除资源的 ID，不虚构资源补齐历史记录。
            require(record.getStatus() != ReserveStatus.PASS
                    || resources.findById(record.getResourceId()).isPresent(), "通过预约资源关联");
        }
        System.out.println("PASS migration checks=" + checks + ", users=" + users.findAll().size()
                + ", resources=" + resources.findAll().size() + ", reservations=" + records.findAll().size());
    }

    /** 检查失败时只输出通用信息。 */
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
