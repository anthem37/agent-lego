/**
 * 知识库横切能力（无状态工具类为主），按主题大致分为：
 * <ul>
 *   <li><b>策略与常量</b>：{@link com.agentlego.backend.kb.support.KbPolicies}、{@link com.agentlego.backend.kb.support.KbLimits}</li>
 *   <li><b>分片</b>：{@link com.agentlego.backend.kb.support.KbChunkExecutor}、{@link com.agentlego.backend.kb.support.KbChunkSlice}、
 *       {@link com.agentlego.backend.kb.support.KbTextChunker}、{@link com.agentlego.backend.kb.support.KbHeadingSectionSplitter}</li>
 *   <li><b>富文本 / Markdown</b>：{@link com.agentlego.backend.kb.support.KbHtmlToMarkdown}、{@link com.agentlego.backend.kb.support.KbRichHtmlPreprocessor}、
 *       {@link com.agentlego.backend.kb.support.KbRichHtmlExpansion}</li>
 *   <li><b>工具绑定与占位符</b>：{@link com.agentlego.backend.kb.support.KbDocumentToolBindings}、{@link com.agentlego.backend.kb.support.KbKnowledgeInlineToolSyntax}、
 *       {@link com.agentlego.backend.kb.support.KbToolPlaceholderExpander}、{@link com.agentlego.backend.kb.support.KbLinkedToolIdsJson}</li>
 *   <li><b>入库向量化输入</b>：{@link com.agentlego.backend.kb.support.KbIngestEmbeddingInputs}</li>
 *   <li><b>RAG 工具结果提取</b>：{@link com.agentlego.backend.kb.support.KbToolResultRootExtractor}</li>
 *   <li><b>控制台召回预览</b>：{@link com.agentlego.backend.kb.support.KbMultiRetrievePreviewRules}（多集合约束）、
 *       {@link com.agentlego.backend.kb.support.KbRetrievePreviewAssembler}（命中 DTO 组装，Spring Bean）</li>
 * </ul>
 * 子包未再拆分，避免 import 噪声；职责以本说明与类 Javadoc 为准。
 */
package com.agentlego.backend.kb.support;
