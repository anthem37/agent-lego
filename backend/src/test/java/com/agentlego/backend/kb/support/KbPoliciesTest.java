package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KbPoliciesTest {

    @Test
    void fullTextEnabled_absentUsesDefault() {
        assertThat(KbPolicies.fullTextEnabled(Map.of(), true)).isTrue();
        assertThat(KbPolicies.fullTextEnabled(Map.of(), false)).isFalse();
        assertThat(KbPolicies.fullTextEnabled(null, false)).isFalse();
    }

    @Test
    void fullTextEnabled_policyOverrides() {
        assertThat(KbPolicies.fullTextEnabled(Map.of("fullTextEnabled", false), true)).isFalse();
        assertThat(KbPolicies.fullTextEnabled(Map.of("fullTextEnabled", true), false)).isTrue();
        assertThat(KbPolicies.fullTextEnabled(Map.of("fullTextEnabled", "false"), true)).isFalse();
    }
}
