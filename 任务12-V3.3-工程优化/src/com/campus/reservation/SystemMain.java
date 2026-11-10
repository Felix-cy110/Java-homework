package com.campus.reservation;

import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.service.UserService;
import com.campus.reservation.service.ResourceService;
import com.campus.reservation.service.ReserveService;
import com.campus.reservation.ui.console.ConsoleUI;
import java.io.IOException;

/** V3.3 启动装配：按顺序加载单例并启动审核视图。 */
public class SystemMain {
    /** 依次装配 DAO、Service、UI，启动失败统一交给视图展示。 */
    public static void main(String[] args) {
        ConsoleUI.printBanner();
        String loadingFile = "user.txt";
        int exitCode;
        try {
            DaoFactory.getUserDao();
            loadingFile = "resource.txt";
            DaoFactory.getResourceDao();
            loadingFile = "reserve.txt";
            DaoFactory.getReserveRecordDao();
            ConsoleUI ui = new ConsoleUI(UserService.getInstance(),
                    ResourceService.getInstance(), ReserveService.getInstance());
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
