package com.agentlego.backend.kb.support;

import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbRichHtmlExpansionTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void toolTag_becomesMentionToken() {
        String html = "<p><span class=\"kb-knowledge-inline\" data-type=\"tool\" data-tool-code=\"my_tool\">x</span></p>";
        ToolRepository repo = idRepo("1800000000001000001", "my_tool");
        String linked = "[\"1800000000001000001\"]";
        KbRichHtmlExpansion.ExpandOutcome out = KbRichHtmlExpansion.expandForIngest(html, linked, repo, OM);
        assertThat(out.html()).contains("{{tool:my_tool}}");
        assertThat(out.bindingsJson()).contains("\"mappings\":[]");
    }

    @Test
    void toolField_generatesPlaceholderAndMapping() {
        String html = "<p><span data-type=\"tool_field\" data-tool-code=\"my_tool\" data-tool-field=\"data.orderNo\">f</span></p>";
        ToolRepository repo = idRepo("1800000000001000001", "my_tool");
        String linked = "[\"1800000000001000001\"]";
        KbRichHtmlExpansion.ExpandOutcome out = KbRichHtmlExpansion.expandForIngest(html, linked, repo, OM);
        assertThat(out.html()).contains("{{tool_field:my_tool.data.orderNo}}");
        assertThat(out.bindingsJson()).contains("1800000000001000001");
        assertThat(out.bindingsJson()).contains("$.data.orderNo");
        assertThat(out.bindingsJson()).contains("\"placeholder\":\"tool_field:my_tool.data.orderNo\"");
    }

    @Test
    void toolField_quillKbKnowledgeInlineWithoutDataType_stillExpands() {
        String html =
                "<p><span class=\"kb-knowledge-inline\" data-tool-code=\"my_tool\" data-tool-field=\"data.orderNo\">x</span></p>";
        ToolRepository repo = idRepo("1800000000001000001", "my_tool");
        String linked = "[\"1800000000001000001\"]";
        KbRichHtmlExpansion.ExpandOutcome out = KbRichHtmlExpansion.expandForIngest(html, linked, repo, OM);
        assertThat(out.html()).contains("{{tool_field:my_tool.data.orderNo}}");
        assertThat(out.bindingsJson()).contains("$.data.orderNo");
    }

    @Test
    void toolField_afterExpansion_markdownStillContainsPlaceholderToken() {
        String html =
                "<p>前<span class=\"kb-knowledge-inline\" data-tool-code=\"my_tool\" data-tool-field=\"a.b\">x</span>后</p>";
        ToolRepository repo = idRepo("1800000000001000001", "my_tool");
        String linked = "[\"1800000000001000001\"]";
        KbRichHtmlExpansion.ExpandOutcome out = KbRichHtmlExpansion.expandForIngest(html, linked, repo, OM);
        String md = KbHtmlToMarkdown.convert(out.html());
        assertThat(md).containsPattern("\\{\\{\\s*tool_field:my_tool\\.a\\.b\\s*\\}\\}");
    }

    @Test
    void tool_quillKbKnowledgeInlineWithoutDataType_stillExpands() {
        String html = "<p><span class=\"kb-knowledge-inline\" data-tool-code=\"my_tool\">x</span></p>";
        ToolRepository repo = idRepo("1800000000001000001", "my_tool");
        String linked = "[\"1800000000001000001\"]";
        KbRichHtmlExpansion.ExpandOutcome out = KbRichHtmlExpansion.expandForIngest(html, linked, repo, OM);
        assertThat(out.html()).contains("{{tool:my_tool}}");
    }

    @Test
    void toolField_unlinkedTool_throws() {
        String html = "<span data-type=\"tool_field\" data-tool-code=\"x\" data-tool-field=\"a\"></span>";
        ToolRepository repo = idRepo("1", "other");
        assertThatThrownBy(() -> KbRichHtmlExpansion.expandForIngest(html, "[\"1\"]", repo, OM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析工具");
    }

    private static ToolRepository idRepo(String id, String name) {
        return new ToolRepository() {
            @Override
            public Optional<ToolAggregate> findById(String toolId) {
                if (!id.equals(toolId)) {
                    return Optional.empty();
                }
                ToolAggregate a = new ToolAggregate();
                a.setId(id);
                a.setName(name);
                return Optional.of(a);
            }

            @Override
            public String save(ToolAggregate aggregate) {
                return "";
            }

            @Override
            public void update(ToolAggregate aggregate) {
            }

            @Override
            public int deleteById(String id) {
                return 0;
            }

            @Override
            public boolean existsOtherWithNameIgnoreCase(String name, String excludeId) {
                return false;
            }

            @Override
            public List<ToolAggregate> findAll() {
                return List.of();
            }

            @Override
            public long countByQuery(String q, String toolType) {
                return 0;
            }

            @Override
            public List<ToolAggregate> findPageByQuery(String q, String toolType, long offset, int limit) {
                return List.of();
            }
        };
    }
}
