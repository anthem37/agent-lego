package com.agentlego.backend.model.support;

import org.springframework.stereotype.Component;

/**
 * 默认环境变量来源：直接读取 {@link System#getenv(String)}。
 */
@Component
public class SystemEnvVariables implements EnvVariables {

    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}

