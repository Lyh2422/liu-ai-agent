# PGVector 向量索引

## 作用

MySQL 继续保存账号、会话和知识文档原文；PGVector 只保存知识切片的文本、元数据和 embedding。
查询时，系统默认用 DashScope `qwen3.7-text-embedding-flash` 把问题转换为向量，在 PGVector 中执行余弦相似度检索，
再与本地 BM25 结果通过 RRF 融合。

PGVector 使用 HNSW 索引。它主要解决原 `SimpleVectorStore` 的三个问题：

- **重启丢失**：内存向量在 JVM 退出后消失，PGVector 会持久化到 Docker Volume。
- **单机容量受限**：向量不再全部占用应用堆内存，数据库可以独立扩容和维护索引。
- **多实例不一致**：多个后端实例可以访问同一份向量索引，不需要各自维护一份内存副本。

PGVector 不替代 BM25。向量召回负责语义相似，BM25 负责关键词精确匹配，两路结果仍由 RRF 融合。
运行期间 PGVector 连接异常时，本次请求会停止重复访问向量库并继续执行 BM25。

## 一致性与复用

知识版本由全部切片 ID 排序后计算 SHA-256 得到。写入 PGVector 的每条记录都带有
`knowledgeVersion` 和原始 `chunkId`：

1. 应用启动时先计算当前版本，只补写 PGVector 中缺少的 ID；知识未变化时不会重新生成 embedding。
2. 管理员修改知识文档时，先生成并写入一个完整的新版本，再保存 MySQL 文档并切换检索快照。
3. MySQL 写入失败时删除未发布版本；发布成功后保留上一代向量，下一次发布时再清理。
4. 回答缓存键包含知识版本，知识变更后不会复用旧版本回答。

这个设计避免了“新旧切片混查”，也不会在应用重启后无条件重新调用 embedding 模型。

## 本地启动

复制配置并启动 MySQL 与 PGVector：

```sh
cp .env.mysql.example .env.mysql
docker compose --env-file .env.mysql -f compose.mysql.yml up -d
```

启动后端前加载向量库配置：

```sh
set -a
source .env.mysql
set +a

export CHAT_DATABASE_URL='jdbc:mysql://127.0.0.1:3306/liu_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
./mvnw spring-boot:run
```

默认连接地址是 `jdbc:postgresql://localhost:5432/liu_ai_vectors`。首次启动时，
Spring AI 会创建 `vector`、`hstore`、`uuid-ossp` 扩展以及
`love_knowledge_vectors_qwen37_flash` 表和 HNSW 索引。

检查已保存的向量：

```sh
docker compose --env-file .env.mysql -f compose.mysql.yml exec vector-database \
  psql -U liu_ai_agent -d liu_ai_vectors \
  -c "select count(*) as vectors, metadata->>'knowledgeVersion' as version from love_knowledge_vectors_qwen37_flash group by version;"
```

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_EMBEDDING_MODEL` | `qwen3.7-text-embedding-flash` | DashScope Embedding 模型 ID |
| `VECTOR_DATABASE_ENABLED` | `false` | 是否启用 PGVector；生产 Compose 会强制开启 |
| `VECTOR_DATABASE_URL` | `jdbc:postgresql://localhost:5432/liu_ai_vectors` | JDBC 地址 |
| `VECTOR_DATABASE_USERNAME` | `liu_ai_agent` | 数据库用户 |
| `VECTOR_DATABASE_PASSWORD` | 本地开发值 | 数据库密码，生产环境必须替换 |
| `VECTOR_DATABASE_SCHEMA` | `public` | schema 名称 |
| `VECTOR_DATABASE_TABLE` | `love_knowledge_vectors_qwen37_flash` | 向量表名称 |
| `VECTOR_DATABASE_DIMENSIONS` | `0` | 0 表示从 EmbeddingModel 读取维度 |
| `VECTOR_DATABASE_MAX_POOL_SIZE` | `5` | 独立 Hikari 连接池容量 |

更换 embedding 模型且向量维度发生变化时，需要使用新表名，或在确认可以重建后删除旧向量表。
PGVector 表与 embedding 维度不匹配时不能混用。
