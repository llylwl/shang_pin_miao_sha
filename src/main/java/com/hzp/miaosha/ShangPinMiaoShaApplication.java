package com.hzp.miaosha;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商品秒杀热 Key 治理 启动类。
 *
 * @author hzp
 */
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.hzp.miaosha.mapper")
@SpringBootApplication
public class ShangPinMiaoShaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShangPinMiaoShaApplication.class, args);
    }
}
