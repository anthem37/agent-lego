/**
 * 将表单字段组装为后端 vectorStoreConfig（与知识库创建集合时一致，经 KbVectorStoreConfigValidator）。
 */
export type VectorStoreConnectionFormValues = {
    milvusHost?: string;
    milvusPort?: number;
    milvusCollectionName?: string;
    milvusToken?: string;
    milvusSecure?: boolean;
    qdrantHost?: string;
    qdrantPort?: number;
    qdrantCollectionName?: string;
    qdrantApiKey?: string;
    qdrantSecure?: boolean;
    qdrantDistance?: string;
};

export function buildVectorStoreConnectionConfig(
    kind: "MILVUS" | "QDRANT",
    values: VectorStoreConnectionFormValues,
): Record<string, unknown> {
    if (kind === "QDRANT") {
        const vectorStoreConfig: Record<string, unknown> = {
            host: (values.qdrantHost ?? "").trim(),
            port: values.qdrantPort ?? 6333,
        };
        const qColl = (values.qdrantCollectionName ?? "").trim();
        if (qColl) {
            vectorStoreConfig.collectionName = qColl;
        }
        const qk = values.qdrantApiKey?.trim();
        if (qk) {
            vectorStoreConfig.apiKey = qk;
        }
        if (values.qdrantSecure) {
            vectorStoreConfig.secure = true;
        }
        const dist = values.qdrantDistance?.trim();
        if (dist) {
            vectorStoreConfig.distance = dist;
        }
        return vectorStoreConfig;
    }
    const vectorStoreConfig: Record<string, unknown> = {
        host: (values.milvusHost ?? "").trim(),
        port: values.milvusPort ?? 19530,
    };
    const mColl = (values.milvusCollectionName ?? "").trim();
    if (mColl) {
        vectorStoreConfig.collectionName = mColl;
    }
    const tok = values.milvusToken?.trim();
    if (tok) {
        vectorStoreConfig.token = tok;
    }
    if (values.milvusSecure) {
        vectorStoreConfig.secure = true;
    }
    return vectorStoreConfig;
}
