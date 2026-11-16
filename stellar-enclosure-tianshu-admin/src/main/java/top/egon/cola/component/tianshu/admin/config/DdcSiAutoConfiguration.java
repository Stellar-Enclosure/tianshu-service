package top.egon.cola.component.tianshu.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapper;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapperImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Auto-configuration registering SI configuration properties, isolated index executor, and proto mapper. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DdcSiProperties.class)
public class DdcSiAutoConfiguration {

    @Bean(name = "siIndexTaskExecutor")
    @ConditionalOnMissingBean(name = "siIndexTaskExecutor")
    public Executor siIndexTaskExecutor(DdcSiProperties properties) {
        DdcSiProperties.IndexExecutorProperties cfg = properties.getIndexExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("tianshu-si-index-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) Math.max(1L, cfg.getAwaitTermination().toSeconds()));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "ddcSiProtoMapperImpl")
    @ConditionalOnMissingBean(DdcSiProtoMapper.class)
    public DdcSiProtoMapper ddcSiProtoMapper() {
        return new DdcSiProtoMapperImpl();
    }
}
