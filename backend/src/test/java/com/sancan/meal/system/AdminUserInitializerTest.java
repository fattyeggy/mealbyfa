package com.sancan.meal.system;

import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import com.sancan.meal.system.user.dal.mapper.AdminUserMapper;
import com.sancan.meal.system.user.service.AdminUserInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 整改 P0：演示账号不得出现在体验/生产环境。 */
class AdminUserInitializerTest {

    private final AdminUserMapper mapper = mock(AdminUserMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private AdminUserInitializer initializer(boolean demo, String user, String pass) {
        when(encoder.encode(anyString())).thenReturn("encoded");
        return new AdminUserInitializer(mapper, encoder, demo, user, pass, "超级管理员");
    }

    @Test
    @DisplayName("关闭演示模式且未配置管理员时不创建任何账号")
    void noSeedWhenDisabled() {
        when(mapper.selectByUsername(anyString())).thenReturn(null);
        initializer(false, "", "").run(null);
        verify(mapper, never()).insert(any(AdminUserDO.class));
    }

    @Test
    @DisplayName("关闭演示模式时用环境变量创建管理员")
    void seedFromEnv() {
        when(mapper.selectByUsername("ops-admin")).thenReturn(null);
        initializer(false, "ops-admin", "S3cret!").run(null);
        verify(mapper).insert(any(AdminUserDO.class));
    }

    @Test
    @DisplayName("关闭演示模式时停用残留的演示账号")
    void disablesResidualDemoAccounts() {
        AdminUserDO demo = new AdminUserDO();
        demo.setUsername("ops");
        demo.setStatus(0);
        when(mapper.selectByUsername("ops")).thenReturn(demo);
        when(mapper.selectByUsername("admin")).thenReturn(null);
        when(mapper.selectByUsername("finance")).thenReturn(null);
        when(mapper.selectByUsername("auditor")).thenReturn(null);

        initializer(false, "", "").run(null);

        assertThat(demo.getStatus()).isEqualTo(1);
        verify(mapper).updateById(demo);
    }

    @Test
    @DisplayName("演示模式仍保留 4 个本地账号")
    void demoModeSeeds() {
        when(mapper.selectByUsername(anyString())).thenReturn(null);
        initializer(true, "", "").run(null);
        verify(mapper, org.mockito.Mockito.times(4)).insert(any(AdminUserDO.class));
    }
}
