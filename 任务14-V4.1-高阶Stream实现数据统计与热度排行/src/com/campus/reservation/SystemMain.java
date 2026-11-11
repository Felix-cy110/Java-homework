package com.campus.reservation;

import com.campus.reservation.dao.DaoFactory;
import com.campus.reservation.controller.UserController;
import com.campus.reservation.controller.ResourceController;
import com.campus.reservation.controller.ReserveController;
import com.campus.reservation.view.MainView;
import java.io.IOException;

/** 启动装配：加载 DAO 数据，启动 MVC 主视图；具体版本由视图展示。 */
public class SystemMain {
    /** 按文件顺序加载 DAO 并装配控制器，所有提示由视图展示。 */
    public static void main(String[] args) {
        MainView.printBanner();
        String loadingFile = "user.txt";
        int exitCode;
        try {
            DaoFactory.getUserDao();
            loadingFile = "resource.txt";
            DaoFactory.getResourceDao();
            loadingFile = "reserve.txt";
            DaoFactory.getReserveRecordDao();
            MainView view = new MainView(new UserController(),
                    new ResourceController(), new ReserveController());
            exitCode = view.start();
        } catch (IOException e) {
            MainView.showStartupError("系统错误：数据加载失败，请检查 " + loadingFile
                    + " 的内容、占用或访问权限后重试。");
            exitCode = 1;
        } catch (RuntimeException e) {
            MainView.showStartupError("系统错误：程序运行异常，请重新启动后重试。");
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
