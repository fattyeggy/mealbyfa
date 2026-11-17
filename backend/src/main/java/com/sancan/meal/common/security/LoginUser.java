package com.sancan.meal.common.security;

import java.io.Serializable;
import java.util.Set;

/** 当前登录主体，作为令牌载荷在服务端保存。 */
public class LoginUser implements Serializable {

    private Long id;
    private UserType userType;
    private String nickname;
    private Set<String> roles = Set.of();

    public LoginUser() {
    }

    public LoginUser(Long id, UserType userType, String nickname, Set<String> roles) {
        this.id = id;
        this.userType = userType;
        this.nickname = nickname;
        this.roles = roles == null ? Set.of() : roles;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
