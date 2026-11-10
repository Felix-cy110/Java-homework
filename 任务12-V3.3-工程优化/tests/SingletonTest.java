import com.campus.reservation.dao.*;
import com.campus.reservation.entity.*;
import com.campus.reservation.service.*;
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
                ReserveRecordDao.class, UserService.class, ResourceService.class, ReserveService.class}) {
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
        require(ReserveService.getInstance() == ReserveService.getInstance(), "预约服务身份");

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
        UserService userService = UserService.getInstance();
        for (User user : users.findAll()) {
            require(userService.loginUser(user.getUsername(), user.getPassword()).isSuccess(), "登录");
            require(ResourceService.getInstance().queryResources(user, "").isSuccess(), "资源查询");
            require(("admin".equals(user.getRole())
                    ? ReserveService.getInstance().queryAllReservations(user)
                    : ReserveService.getInstance().queryMyReservations(user)).isSuccess(), "预约查询");
        }
        for (ReserveRecord record : records.findAll()) {
            require(users.findByUsername(record.getApplicantUsername()).isPresent(), "预约账号关联");
            require(resources.findById(record.getResourceId()).isPresent(), "预约资源关联");
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
