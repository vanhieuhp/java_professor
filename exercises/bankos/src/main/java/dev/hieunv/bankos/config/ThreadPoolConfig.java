package dev.hieunv.bankos.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean(name = "cpuTaskExecutor")
    public ExecutorService cpuTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cores,                              // corePoolSize
                cores * 2,                          // maxPoolSize
                60L, TimeUnit.SECONDS,              // keepAlive
                new LinkedBlockingQueue<>(100),     // bounded queue
                new ThreadFactoryBuilder()
                        .setNameFormat("cpu-pool-%d")
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure
        );
    }

    @Bean(name = "ioTaskExecutor")
    public ExecutorService ioTaskExecutor() {
        return new ThreadPoolExecutor(
                20,                                 // corePoolSize
                100,                                // maxPoolSize
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadFactoryBuilder()
                        .setNameFormat("io-pool-%d")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy() // fail fast when full
        );
    }

    // Java 21 virtual thread executor — ideal for I/O tasks
    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
