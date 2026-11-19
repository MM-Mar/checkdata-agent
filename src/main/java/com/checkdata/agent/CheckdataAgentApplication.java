package com.checkdata.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * checkdata-agent 启动类。
 * <p>
 * 基于 spring-ai-agentx 框架开发的 checkdata-agent 数据分析智能体
 */
@SpringBootApplication
@MapperScan({
        "com.checkdata.agent.mapper",
        "com.checkdata.agent.sys.mapper"
})
@EnableScheduling
public class CheckdataAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckdataAgentApplication.class, args);
    }
}