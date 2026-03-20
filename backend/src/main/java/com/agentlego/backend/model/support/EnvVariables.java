package com.agentlego.backend.model.support;

/**
 * 环境变量读取抽象，便于在单元测试中注入假实现。
 */
public interface EnvVariables {
    String get(String name);
}

