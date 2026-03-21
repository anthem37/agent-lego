package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbVectorMathTest {

    @Test
    void cosine_sameVector_isOne() {
        float[] v = {1f, 0f, 0f};
        assertEquals(1d, KbVectorMath.cosine(v, v), 1e-6);
    }

    @Test
    void cosine_orthogonal_isZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0d, KbVectorMath.cosine(a, b), 1e-6);
    }

    @Test
    void cosine_mismatchLength_negative() {
        assertTrue(KbVectorMath.cosine(new float[]{1f}, new float[]{1f, 1f}) < 0);
    }
}
