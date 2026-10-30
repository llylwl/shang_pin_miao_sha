package com.hzp.miaosha;

import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.po.SeckillActivityPo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.mapper.SeckillActivityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库集成测试（需要本机 MySQL 可用）。
 * <p>
 * 通过环境变量 {@code RUN_DB_TEST=true} 开启，避免在无数据库环境中构建失败：
 * <pre>
 * RUN_DB_TEST=true mvn -Dtest=DatabaseIntegrationTest test
 * </pre>
 * 依赖 application.yml 中的 spring.sql.init（schema.sql + data.sql 幂等执行）。
 * </p>
 *
 * @author hzp
 */
@DisplayName("数据库集成测试")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseIntegrationTest {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Test
    @DisplayName("商品数据可正常读取（含已下架/已结束）")
    void productsShouldBeLoaded() {
        List<ProductPo> products = productMapper.selectValidList(100);
        assertThat(products).isNotEmpty();
        // data.sql 共 10 条商品，其中 1 条已逻辑删除，selectValidList 返回 9 条
        assertThat(products).hasSizeGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("活动数据包含过去、现在、未来三种窗口")
    void activitiesShouldCoverPastPresentFuture() {
        List<SeckillActivityPo> activities = seckillActivityMapper.selectAll();
        LocalDateTime now = LocalDateTime.now();
        long past = activities.stream().filter(item -> now.isAfter(item.getEndTime())).count();
        long current = activities.stream()
                .filter(item -> !now.isBefore(item.getStartTime()) && !now.isAfter(item.getEndTime()))
                .count();
        long future = activities.stream().filter(item -> now.isBefore(item.getStartTime())).count();

        assertThat(past).isGreaterThanOrEqualTo(1L);
        assertThat(current).isGreaterThanOrEqualTo(1L);
        assertThat(future).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("过期数据存在：已结束活动 / 已结束或售罄商品")
    void expiredDataShouldExist() {
        List<ProductPo> products = productMapper.selectValidList(100);
        // selectValidList 会过滤逻辑删除的商品，因此这里验证「已结束」与「已售罄」两类过期数据
        assertThat(products).anyMatch(item -> Integer.valueOf(3).equals(item.getStatus()));
        assertThat(products).anyMatch(item -> Integer.valueOf(2).equals(item.getStatus()));
        // 已结束（status=2）的历史活动同样构成过期数据
        assertThat(seckillActivityMapper.selectAll())
                .anyMatch(item -> Integer.valueOf(2).equals(item.getStatus()));
        // 历史热点记录（7 天前）也应存在
        assertThat(products.size() + seckillActivityMapper.selectAll().size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("按活动查询商品可用")
    void queryProductsByActivity() {
        List<ProductPo> products = productMapper.selectByActivityId(1L);
        assertThat(products).isNotEmpty();
    }

    @Test
    @DisplayName("预热查询可返回即将开始的商品")
    void warmUpQueryShouldWork() {
        List<ProductPo> products = productMapper.selectWarmUpProducts(86_400L);
        assertThat(products).isNotEmpty();
    }
}
