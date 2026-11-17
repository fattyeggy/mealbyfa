package com.sancan.meal.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 后台账号初始化。
 *
 * <p>演示账号只在本地开发（demo-accounts=true）创建；
 * 体验与生产环境通过环境变量注入首个管理员，并停用残留的演示账号。
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    /** 演示账号名单；关闭演示模式时会统一停用。 */
    private static final List<String> DEMO_USERNAMES = List.of("admin", "ops", "finance", "auditor");

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean demoAccounts;
    private final String seedUsername;
    private final String seedPassword;
    private final String seedNickname;

    public AdminUserInitializer(AdminUserMapper adminUserMapper,
                                PasswordEncoder passwordEncoder,
                                @Value("${meal.admin.demo-accounts:false}") boolean demoAccounts,
                                @Value("${meal.admin.username:}") String seedUsername,
                                @Value("${meal.admin.password:}") String seedPassword,
                                @Value("${meal.admin.nickname:超级管理员}") String seedNickname) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.demoAccounts = demoAccounts;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
        this.seedNickname = seedNickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoAccounts) {
            seed("admin", "admin123", "超级管理员", "SUPER_ADMIN");
            seed("ops", "ops123", "运营小张", "OPS");
            seed("finance", "finance123", "财务小李", "FINANCE");
            seed("auditor", "auditor123", "审计只读", "AUDITOR");
            log.warn("当前为本地演示模式，已确保 4 个演示账号存在；部署环境请设置 meal.admin.demo-accounts=false");
            return;
        }
        if (!seedUsername.isBlank() && !seedPassword.isBlank()) {
            seed(seedUsername, seedPassword, seedNickname, "SUPER_ADMIN");
        } else {
            log.warn("未配置初始管理员：请设置 ADMIN_USERNAME / ADMIN_PASSWORD，或由 DBA 预置账号");
        }
        disableDemoAccounts();
    }

    /** 关闭演示模式时，把残留的演示账号停用，避免已知密码进入体验/生产环境。 */
    private void disableDemoAccounts() {
        for (String username : DEMO_USERNAMES) {
            if (username.equalsIgnoreCase(seedUsername)) {
                continue;
            }
            AdminUserDO user = adminUserMapper.selectByUsername(username);
            if (user != null && (user.getStatus() == null || user.getStatus() == 0)) {
                user.setStatus(1);
                adminUserMapper.updateById(user);
                log.warn("已停用残留演示账号：{}（体验/生产环境不允许已知密码账号）", username);
            }
        }
    }

    private void seed(String username, String rawPassword, String nickname, String role) {
        if (adminUserMapper.selectByUsername(username) != null) {
            return;
        }
        AdminUserDO user = new AdminUserDO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(0);
        adminUserMapper.insert(user);
        // 不打印密码，避免日志泄露
        log.info("已创建后台账号 {}（角色 {}），请首次登录后立即修改密码", username, role);
    }
}
