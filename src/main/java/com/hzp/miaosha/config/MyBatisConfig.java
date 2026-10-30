package com.hzp.miaosha.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * MyBatis 配置（不依赖 mybatis-spring-boot-starter，显式装配 SqlSessionFactory）。
 *
 * @author hzp
 */
@Configuration(proxyBeanMethods = false)
public class MyBatisConfig {

    /**
     * mapper XML 位置。
     */
    private static final String MAPPER_LOCATIONS = "classpath*:mapper/*.xml";

    /**
     * 别名包。
     */
    private static final String TYPE_ALIASES_PACKAGE = "com.hzp.miaosha.domain.po";

    /**
     * SqlSessionFactory。
     *
     * @param dataSource 数据源
     * @return 工厂
     * @throws Exception 资源加载异常
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeAliasesPackage(TYPE_ALIASES_PACKAGE);
        factoryBean.setMapperLocations(resolveMapperLocations());
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(3000);
        configuration.setCacheEnabled(false);
        factoryBean.setConfiguration(configuration);
        Objects.requireNonNull(factoryBean.getObject(), "SqlSessionFactory 创建失败");
        return factoryBean.getObject();
    }

    /**
     * SqlSessionTemplate。
     *
     * @param sqlSessionFactory 工厂
     * @return 模板
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    private Resource[] resolveMapperLocations() throws java.io.IOException {
        return new PathMatchingResourcePatternResolver().getResources(MAPPER_LOCATIONS);
    }
}
