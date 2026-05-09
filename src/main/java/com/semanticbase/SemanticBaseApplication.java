package com.semanticbase;

import com.semanticbase.cache.CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CacheProperties.class)
public class SemanticBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemanticBaseApplication.class, args);
    }
}
