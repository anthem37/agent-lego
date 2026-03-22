/**
 * 知识库应用层：HTTP 用例编排（{@link com.agentlego.backend.kb.application.service.KbApplicationService}）、
 * 集合/文档存在性（{@link com.agentlego.backend.kb.application.service.KbCollectionAccess}）、
 * 入库准备与收尾（{@link com.agentlego.backend.kb.application.service.KbIngestPayloadPreparer} /
 * {@link com.agentlego.backend.kb.application.service.KbIngestFinalizeRunner}）、
 * 向量检索（{@link com.agentlego.backend.kb.application.service.KbVectorRetrieveRunner}）、
 * DTO（含 {@link com.agentlego.backend.kb.application.dto.KbPreparedIngestPayload}）、MapStruct 映射、
 * 文档校验（{@link com.agentlego.backend.kb.application.validation.KbDocumentValidator}）。
 * <p>
 * RAG 装配与向量检索接口见 {@link com.agentlego.backend.kb.rag}；领域模型见 {@link com.agentlego.backend.kb.domain}。
 */
package com.agentlego.backend.kb.application;
