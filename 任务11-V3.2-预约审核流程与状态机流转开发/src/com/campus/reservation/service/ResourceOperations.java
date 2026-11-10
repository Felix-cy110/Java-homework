package com.campus.reservation.service;

import com.campus.reservation.entity.CampusResource;
import com.campus.reservation.entity.Result;
import com.campus.reservation.entity.User;
import java.util.List;

/** 资源业务契约：视图依赖此小接口，具体实现负责校验和数据访问。 */
public interface ResourceOperations {
    /** 新增资源并在文件保存成功后更新内存集合。 */
    Result<CampusResource> addResource(User user, String name, String type, String location);

    /** 使用 Stream 组合角色过滤、名称模糊匹配和 ID 排序。 */
    Result<List<CampusResource>> queryResources(User user, String keyword);

    /** 修改资源基本信息，保存成功后替换内存中的同一条记录。 */
    Result<CampusResource> updateResource(User user, String id, String name, String type,
            String location, String status);

    /** 只有存在 PASS 预约时禁止删除，PENDING/REJECT/CANCEL 不阻止删除。 */
    Result<Void> deleteResource(User user, String id);
}
