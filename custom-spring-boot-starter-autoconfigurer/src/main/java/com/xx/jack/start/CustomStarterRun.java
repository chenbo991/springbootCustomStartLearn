package com.xx.jack.start;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(JackTemplate.class)
@EnableConfigurationProperties(RedisConfig.class)
public class CustomStarterRun {

    @Autowired
    private RedisConfig redisConfig;

    @Bean
    public JackTemplate jackTemplate() {
        JackTemplate jackTemplate = new JackTemplate(redisConfig);
        return jackTemplate;
    }
}
