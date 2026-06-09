package com.arena.cpj.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "judge0")
public class Judge0Properties {

    private String baseUrl = "http://localhost:2358";
    private String callbackBaseUrl = "http://localhost:8080";
    private double cpuTimeLimit = 1.0;
    private int memoryLimitKb = 262144;
}
