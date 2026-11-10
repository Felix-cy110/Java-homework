package com.campus.reservation;

import com.campus.reservation.dao.UserDao;
import com.campus.reservation.dao.ResourceDao;
import com.campus.reservation.dao.ReserveRecordDao;
import com.campus.reservation.service.UserService;
import com.campus.reservation.service.ResourceService;
import com.campus.reservation.service.ReserveService;
import com.campus.reservation.ui.console.ConsoleUI;
import java.io.IOException;

/** V3.1 启动装配：创建共享依赖并启动视图，不承载业务或文件操作。 */
public class SystemMain {
    /** 依次装配 DAO、Service、UI，启动失败统一交给视图展示。 */
    public static void main(String[] args) {
        ConsoleUI.printBanner();
        String loadingFile = "user.txt";
        int exitCode;
        try {
            UserDao userDao = new UserDao();
            loadingFile = "resource.txt";
            ResourceDao resourceDao = new ResourceDao();
            loadingFile = "reserve.txt";
            ReserveRecordDao reserveDao = new ReserveRecordDao();
            ConsoleUI ui = new ConsoleUI(new UserService(userDao),
                    new ResourceService(resourceDao, reserveDao),
                    new ReserveService(resourceDao, reserveDao));
            exitCode = ui.run();
        } catch (IOException e) {
            ConsoleUI.showStartupError("系统错误：数据加载失败，请检查 " + loadingFile
                    + " 的内容、占用或访问权限后重试。");
            exitCode = 1;
        } catch (RuntimeException e) {
            ConsoleUI.showStartupError("系统错误：程序运行异常，请重新启动后重试。");
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
