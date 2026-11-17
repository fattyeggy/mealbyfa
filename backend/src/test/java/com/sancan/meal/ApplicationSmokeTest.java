package com.sancan.meal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 上下文与关键 Bean 装配检查。 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(com.sancan.meal.common.security.TokenStore.class)).isNotNull();
    }
}
