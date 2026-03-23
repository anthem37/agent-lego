package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insertPending(KbDocumentDO row);

    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    int updateReingest(
            @Param("id") String id,
            @Param("title") String title,
            @Param("body") String body,
            @Param("bodyRich") String bodyRich,
            @Param("linkedToolIdsJson") String linkedToolIdsJson,
            @Param("toolOutputBindingsJson") String toolOutputBindingsJson,
            @Param("similarQueriesJson") String similarQueriesJson
    );

    KbDocumentDO findById(@Param("id") String id);

    List<KbDocumentDO> findByIds(@Param("ids") List<String> ids);

    List<KbDocumentDO> listByCollectionId(@Param("collectionId") String collectionId);

    int deleteById(@Param("id") String id);

    long countDocumentsReferencingToolId(@Param("toolId") String toolId);
}
