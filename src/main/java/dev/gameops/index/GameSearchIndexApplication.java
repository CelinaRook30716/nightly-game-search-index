package dev.gameops.index;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameIndexConfig.class)
public class GameSearchIndexApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameSearchIndexApplication.class, args);
    }
}
