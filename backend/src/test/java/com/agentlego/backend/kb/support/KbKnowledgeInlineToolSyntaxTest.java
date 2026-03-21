package com.agentlego.backend.kb.support;

import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KbKnowledgeInlineToolSyntaxTest {

    @Test
    void extract_tokens_findsNamesAndNumericIds() {
        var tokens = KbKnowledgeInlineToolSyntax.extractToolMentionTokens("a {{tool:order_q}} b {{tool:18001}}");
        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("order_q"));
        assertTrue(tokens.contains("18001"));
    }

    @Test
    void expand_replacesWhenLinked_byNumericId() {
        ToolRepository repo = mock(ToolRepository.class);
        ToolAggregate t = new ToolAggregate();
        t.setName("查单工具");
        when(repo.findById(eq("99"))).thenReturn(Optional.of(t));

        String out = KbKnowledgeInlineToolSyntax.expandToolMentions(
                "请用 {{tool:99}}",
                repo,
                List.of("99")
        );
        assertEquals("请用 「查单工具」工具", out);
    }

    @Test
    void expand_prefersDisplayLabel() {
        ToolRepository repo = mock(ToolRepository.class);
        ToolAggregate t = new ToolAggregate();
        t.setName("order_query");
        t.setDisplayLabel("订单查询");
        when(repo.findById(eq("1"))).thenReturn(Optional.of(t));
        String out = KbKnowledgeInlineToolSyntax.expandToolMentions("x{{tool:1}}y", repo, List.of("1"));
        assertEquals("x「订单查询」工具y", out);
    }

    @Test
    void expand_resolvesByRuntimeName_caseInsensitive() {
        ToolRepository repo = mock(ToolRepository.class);
        ToolAggregate t = new ToolAggregate();
        t.setName("My_Api");
        when(repo.findById(eq("42"))).thenReturn(Optional.of(t));
        String out = KbKnowledgeInlineToolSyntax.expandToolMentions(
                "调用 {{tool:my_api}}",
                repo,
                List.of("42")
        );
        assertEquals("调用 「My_Api」工具", out);
    }
}
