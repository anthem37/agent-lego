# agent-lego-backend

Spring Boot 3.5 + Java **17**（`pom.xml` 中 `java.version`）。

## 构建与校验

本机默认 `java` 若为 11，Maven 会报「不支持发行版本 17」。请使用 **JDK 17+**（本仓库在 JDK **21** 下已跑通全量测试）：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # 或 17、25 等
cd backend
mvn -q verify          # 编译 + 测试
# 或
mvn -q test
```

## 架构说明

见 [ARCHITECTURE.md](./ARCHITECTURE.md)。

## 记忆策略（测试）

- `MemoryPolicyApplicationServiceTest`（`memorypolicy/application/`）：覆盖 `roughSummaryMaxChars` 落库与 `PUT` 时
  `clearRoughSummaryMaxChars` 清空为 `null` 等语义。

## 知识库应用层拆分（便于维护）

- `KbCollectionAccess`：集合/文档存在性
- `KbIngestPayloadPreparer` / `KbIngestFinalizeRunner`：入库正文与向量写入
- `KbRetrievePreviewAssembler`：召回预览 DTO
- `KbVectorRetrieveRunner`：向量检索（`KbVectorRetrieveEngine` 封装）
- `KbMultiRetrievePreviewRules`：多集合召回约束
