package com.agentlego.backend.kb.support;

/**
 * 将 float 数组格式化为 PostgreSQL pgvector 字面量 {@code [1,2,3,...]}，供 MyBatis {@code CAST(... AS vector)} 使用。
 * <p>
 * 仅由平台生成的浮点序列使用，不包含用户输入。
 */
public final class KbPgVectorLiteral {

    private KbPgVectorLiteral() {
    }

    public static String format(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Double.toString(vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
