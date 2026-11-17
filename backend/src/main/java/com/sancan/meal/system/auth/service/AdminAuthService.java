package com.sancan.meal.system.auth.service;

import com.sancan.meal.common.api.LoginResp;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.common.security.LoginUser;
import com.sancan.meal.common.security.Role;
import com.sancan.meal.common.security.TokenStore;
import com.sancan.meal.common.security.UserType;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public AdminAuthService(AdminUserMapper adminUserMapper,
                            PasswordEncoder passwordEncoder,
                            TokenStore tokenStore) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    public LoginResp login(String username, String password) {
        AdminUserDO user = adminUserMapper.selectByUsername(username);
        // 账号不存在与口令错误返回同一提示，避免账号枚举
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        Set<String> roles = Set.of(Role.of(user.getRole()).name());
        LoginUser loginUser = new LoginUser(user.getId(), UserType.ADMIN, user.getNickname(), roles);
        String token = tokenStore.issue(loginUser);
        return new LoginResp(token, UserType.ADMIN.name(), user.getId(), user.getNickname(), roles);
    }
}
