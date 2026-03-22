package com.agentlego.backend.model.support;

import com.agentlego.backend.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelEmbeddingDimensionsTest {

    @Test
    void fitToCollectionDim_sameLength_unchanged() {
        float[] v = {1f, 2f, 3f};
        assertArrayEquals(v, ModelEmbeddingDimensions.fitToCollectionDim(v, 3));
    }

    @Test
    void fitToCollectionDim_truncatesLonger() {
        float[] v = {1f, 2f, 3f, 4f};
        float[] out = ModelEmbeddingDimensions.fitToCollectionDim(v, 3);
        assertEquals(3, out.length);
        assertArrayEquals(new float[]{1f, 2f, 3f}, out);
    }

    @Test
    void fitToCollectionDim_tooShort_throws() {
        float[] v = {1f, 2f};
        assertThrows(ApiException.class, () -> ModelEmbeddingDimensions.fitToCollectionDim(v, 4));
    }
}
