package com.sancan.meal.system.user.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;

/** 后台运营账号。生产环境由 RuoYi {@code system_users} 承接。 */
@TableName("sys_user")
public class AdminUserDO extends BaseDO {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String mobile;
    /** 手册 §14 角色：SUPER_ADMIN / OPS / NUTRITIONIST / KITCHEN / PURCHASER / FINANCE / COURIER / AUDITOR。 */
    private String role;
    /** 0 启用，1 停用。 */
    private Integer status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
