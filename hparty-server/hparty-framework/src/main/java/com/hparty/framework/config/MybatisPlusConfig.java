package com.hparty.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>数据权限在 Service 层通过 {@code DataScopeHelper} 显式追加条件，
 * 不使用 SQL 解析拦截器 —— 条件可见、可测试，也避免 jsqlparser 版本耦合。</p>
 */
@Configuration
@MapperScan("com.hparty.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 阻止全表更新/删除。
        // 注意它的判定比名字严格：除「无 WHERE」外，**WHERE 里含 OR 的 UPDATE 也会被拒**。
        // 写更新语句时若需要 OR 条件，改成「先 SELECT 出主键，再 UPDATE ... IN (ids)」。
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
