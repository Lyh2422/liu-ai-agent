package com.lyh.liuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiuAiAgentApplication.class, args);
    }

}
