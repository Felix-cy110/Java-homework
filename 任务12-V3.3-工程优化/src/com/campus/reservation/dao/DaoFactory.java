package com.campus.reservation.dao;

import java.io.IOException;

/** 统一提供 DAO 单例，隔离服务层对具体获取方式的依赖。 */
public final class DaoFactory {
    private DaoFactory() {
    }

    /** 获取用户数据访问对象。 */
    public static UserDao getUserDao() throws IOException {
        return UserDao.getInstance();
    }

    /** 获取资源数据访问对象。 */
    public static ResourceDao getResourceDao() throws IOException {
        return ResourceDao.getInstance();
    }

    /** 获取预约数据访问对象。 */
    public static ReserveRecordDao getReserveRecordDao() throws IOException {
        return ReserveRecordDao.getInstance();
    }
}
