package ecommerce.example.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean("scrapingExecutor")
    public Executor scrapingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(10);     // threads running normally
        executor.setMaxPoolSize(10);      // max threads
        executor.setQueueCapacity(200);   // queued tasks
        executor.setThreadNamePrefix("scraper-");

        executor.initialize();
        return executor;
    }
}
