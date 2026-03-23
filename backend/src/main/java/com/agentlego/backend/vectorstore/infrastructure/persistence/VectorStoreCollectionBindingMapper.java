package com.agentlego.backend.vectorstore.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VectorStoreCollectionBindingMapper {

    int insertKb(@Param("profileId") String profileId,
                 @Param("physicalCollectionName") String physicalCollectionName,
                 @Param("kbCollectionId") String kbCollectionId);

    int insertMemoryPolicy(
            @Param("profileId") String profileId,
            @Param("physicalCollectionName") String physicalCollectionName,
            @Param("memoryPolicyId") String memoryPolicyId
    );

    int deleteByMemoryPolicyId(@Param("memoryPolicyId") String memoryPolicyId);

    VectorStoreCollectionBindingDO findByProfileAndPhysicalName(
            @Param("profileId") String profileId,
            @Param("physicalCollectionName") String physicalCollectionName
    );
}
