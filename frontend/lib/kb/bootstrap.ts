/**
 * 知识库控制台首屏/刷新所需并行数据。
 */

import {listKbChunkStrategies, listKbCollections} from "@/lib/kb/api";
import type {KbChunkStrategyMetaDto, KbCollectionDto} from "@/lib/kb/types";
import {listVectorStoreProfiles} from "@/lib/vector-store/api";
import type {VectorStoreProfileDto} from "@/lib/vector-store/types";

export type KbBootstrapResources = {
    collections: KbCollectionDto[];
    chunkStrategies: KbChunkStrategyMetaDto[];
    vectorProfiles: VectorStoreProfileDto[];
};

export async function loadKbBootstrapResources(): Promise<KbBootstrapResources> {
    const [collections, chunkStrategies, vectorProfiles] = await Promise.all([
        listKbCollections(),
        listKbChunkStrategies().catch(() => [] as KbChunkStrategyMetaDto[]),
        listVectorStoreProfiles().catch(() => [] as VectorStoreProfileDto[]),
    ]);
    return {collections, chunkStrategies, vectorProfiles};
}
