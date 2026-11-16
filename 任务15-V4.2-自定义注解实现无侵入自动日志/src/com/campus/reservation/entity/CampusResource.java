package com.campus.reservation.entity;

/** 校园资源实体：只承载资源信息，校验和权限判断由业务方法负责。 */
public class CampusResource {
    private String id;
    private String name;
    private String type;
    private String status;
    private String location;

    /** 创建空实体，供调用方通过 setter 填充字段。 */
    public CampusResource() {
    }

    /** 使用调用方提供的五个字段构造资源，不生成业务默认值。 */
    public CampusResource(String id, String name, String type, String status, String location) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.location = location;
    }

    // 通过访问方法读写私有字段，实体不包含业务规则。
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /** 展示五个字段，供管理员和学生列表按各自权限输出。 */
    @Override
    public String toString() {
        return "CampusResource{id='" + id + "', name='" + name + "', type='" + type
                + "', status='" + status + "', location='" + location + "'}";
    }
}
