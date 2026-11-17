package com.sancan.meal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 三餐私教 meal 自研模块入口。
 *
 * <p>本服务承载交接手册 §11「RuoYi 复用与自研边界」中的自研部分：
 * 周菜单、营养计划、营养点计价、蛋白替换、锁单快照、厨房生产与餐饮成本。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan("com.sancan.meal.**.mapper")
public class MealApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealApplication.class, args);
    }
}
