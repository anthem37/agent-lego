package com.agentlego.backend.eval.application.service;

import java.util.Map;

/**
 * 单次评测运行的内存结果（metrics + trace），仅供 {@link EvaluationApplicationService} 使用。
 */
final class EvaluationRunResult {
    private final Map<String, Object> metrics;
    private final Map<String, Object> trace;

    EvaluationRunResult(Map<String, Object> metrics, Map<String, Object> trace) {
        this.metrics = metrics;
        this.trace = trace;
    }

    Map<String, Object> metrics() {
        return metrics;
    }

    Map<String, Object> trace() {
        return trace;
    }
}
