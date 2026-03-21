package com.agentlego.backend.model.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelEmbeddingDimensionsTest {

    @Test
    void padForPgStorage_padsShort() {
        float[] s = {1f, 2f, 3f};
        float[] p = ModelEmbeddingDimensions.padForPgStorage(s);
        assertEquals(ModelEmbeddingDimensions.PGVECTOR_STORED_DIM, p.length);
        assertEquals(1f, p[0]);
        assertEquals(2f, p[1]);
        assertEquals(3f, p[2]);
        assertEquals(0f, p[3]);
    }

    @Test
    void padForPgStorage_truncatesLong() {
        float[] longVec = new float[ModelEmbeddingDimensions.PGVECTOR_STORED_DIM + 10];
        for (int i = 0; i < longVec.length; i++) {
            longVec[i] = i;
        }
        float[] p = ModelEmbeddingDimensions.padForPgStorage(longVec);
        assertEquals(ModelEmbeddingDimensions.PGVECTOR_STORED_DIM, p.length);
        assertEquals(0f, p[0]);
    }
}
