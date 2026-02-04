package play.lab.config.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CountDownLatch;

@Configuration
public class AppConfig {
    @Bean
    public CountDownLatch aeronStarted() {
        return new CountDownLatch(1);
    }
    @Bean
    public AeronService aeronService() {
        return new AeronService(aeronStarted());
    }
}
