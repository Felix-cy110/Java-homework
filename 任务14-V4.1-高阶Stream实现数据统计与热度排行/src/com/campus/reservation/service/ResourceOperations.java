package com.campus.reservation.service;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.common.Result;
import com.campus.reservation.entity.User;
import java.util.List;

/** 资源业务契约：视图依赖此小接口，具体实现负责校验和数据访问。 */
public interface ResourceOperations {
    /**
     * 新增资源并在文件保存成功后更新内存集合。
     *
     * @param user 当前操作用户，用于校验管理员权限
     * @param name 资源名称
     * @param type 资源类型
     * @param location 资源位置
     */
    Result<CampusResource> addResource(User user, String name, String type, String location);

    /**
     * 使用 Stream 组合角色过滤、名称模糊匹配和 ID 排序。
     *
     * @param user 当前操作用户，用于校验身份和确定可见范围
     * @param keyword 资源名称关键词，为空时不按名称筛选
     */
    Result<List<CampusResource>> queryResources(User user, String keyword);

    /**
     * 修改资源基本信息，保存成功后替换内存中的同一条记录。
     *
     * @param user 当前操作用户，用于校验管理员权限
     * @param id 要修改的资源编号
     * @param name 新的资源名称
     * @param type 新的资源类型
     * @param location 新的资源位置
     * @param status 新的资源状态
     */
    Result<CampusResource> updateResource(User user, String id, String name, String type,
            String location, String status);

    /**
     * 只有存在 PASS 预约时禁止删除，PENDING/REJECT/CANCEL 不阻止删除。
     *
     * @param user 当前操作用户，用于校验管理员权限
     * @param id 要删除的资源编号
     */
    Result<Void> deleteResource(User user, String id);
}
