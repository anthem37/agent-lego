package com.agentlego.backend.kb.support;

/**
 * 纯 Java 余弦相似度，仅用于单测断言数学性质。
 * <p>
 * <b>生产检索</b>由 PostgreSQL <b>pgvector</b> 在 SQL 中计算：索引与排序使用
 * {@code embedding_vec <=> query_vec}（{@code vector_cosine_ops}，即<strong>余弦距离</strong>）；
 * API 层展示的 {@code similarity} 多为 {@code 1 - 距离}，仍在 SQL 里完成，见 {@code KbChunkMapper.xml}。
 */
public final class KbVectorMath {

    private KbVectorMath() {
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return -1d;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i];
            double y = b[i];
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na <= 0 || nb <= 0) {
            return -1d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
