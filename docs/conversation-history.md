# 历史对话与 Agent Memory

两个 AI 入口都支持持久会话、按 token 预算组装上下文、滚动摘要和用户明确表达的结构化事实。

## 小白版：三层笔记

可以把记忆理解成三层：

1. **原始聊天记录**：完整保存，用于页面回看和审计。
2. **会话摘要**：对话太长时，把较早内容压成有上限的摘要，最近内容仍保留原文。
3. **跨会话事实**：像“我叫小林”、“我对花生过敏”这类第一人称明确陈述，可在同一账号的新会话中继续使用。

模型每次仍然是无状态调用。“记得”是后端在每次请求前把上述记忆组装进 Prompt，不是模型自己永久保存了账号信息。

## 专业版：数据结构和组装流程

| 数据表 | 内容 | 性质 |
| --- | --- | --- |
| `chat_conversations` | 会话归属、应用类型、标题和时间 | 主数据 |
| `chat_turns` | 每轮问题、回复和生成状态 | 可审计原始数据 |
| `conversation_memories` | 有界的滚动摘要、已摘要到的 turn ID | 可重建派生数据 |
| `user_memory_facts` | 事实类型、键、值、置信度和来源会话/turn | 可追溯派生数据 |

`ConversationMemoryManager` 根据以下顺序构建模型上下文：

```text
模型输入总预算
  - 当前用户消息
  - 预留给系统提示 / RAG / 工具的预算
  = 记忆与原始历史可用预算

可用预算 -> [非可信记忆数据系统消息] + [从新到旧选中的连续完整轮次]
```

默认输入预算是 12000 tokens，预留 4000，记忆数据最多占 2000。项目不依赖特定模型 tokenizer，使用保守估算：中文大致每个码点一个 token，ASCII 字母数字约四个字符一个 token，并为每条消息预留协议开销。因此短对话可以携带超过五轮，长对话则自动减少，而不再是固定“最近五轮”。

当未摘要的完整轮次超过原文目标预算时，系统将较早轮次合并到滚动摘要，至少保留最新一轮原文。摘要有字符上限；原始 `chat_turns` 不会被摘要覆盖或删除。

事实提取仅接受受支持的第一人称明确句式（姓名、专业、过敏、喜欢、不喜欢），不保存模型推测。事实会绑定来源；删除来源会话或超期清理时，相关派生记忆一并删除。记忆被包在标记为“非可信历史数据”的系统消息中，当前用户指令冲突时以当前指令为准。

## API

以下路径相对于 `/api`，均使用当前 JWT 账号：

| 方法与路径 | 作用 |
| --- | --- |
| `POST /ai/conversations` | 新建 LOVE 或 MANUS 会话 |
| `GET /ai/conversations?appType=LOVE` | 获取当前用户会话 |
| `GET /ai/conversations/{id}` | 获取完整可见历史 |
| `DELETE /ai/conversations/{id}` | 删除会话、原始轮次和它派生的记忆 |
| `GET /ai/memory/facts` | 查看当前用户的结构化事实 |
| `DELETE /ai/memory/facts/{id}` | 删除一条自己的事实记忆 |
| `POST /ai/love_app/chat/sse` | 情感陪伴 SSE |
| `POST /ai/manus/chat` | Manus SSE |

`ConversationStore.begin()` 在短事务内锁定会话行，校验账号和应用归属，阻止同一会话同时生成两轮。`ConversationChatService` 先批量提交回复片段，再发送 SSE `delta`；完成、异常和取消分别记录为 `COMPLETED`、`FAILED` 和 `INTERRUPTED`。

## 配置

```yaml
app:
  conversation:
    memory:
      model-input-token-budget: 12000
      reserved-token-budget: 4000
      memory-token-budget: 2000
      raw-recent-token-target: 5000
      max-candidate-turns: 100
      max-summary-chars: 6000
      max-facts-in-context: 50
```

对应环境变量是 `CHAT_MEMORY_MODEL_INPUT_TOKEN_BUDGET`、`CHAT_MEMORY_RESERVED_TOKEN_BUDGET`、`CHAT_MEMORY_TOKEN_BUDGET`、`CHAT_MEMORY_RAW_RECENT_TOKEN_TARGET`、`CHAT_MEMORY_MAX_CANDIDATE_TURNS`、`CHAT_MEMORY_MAX_SUMMARY_CHARS` 和 `CHAT_MEMORY_MAX_FACTS_IN_CONTEXT`。

## 存储与迁移

本地和生产默认都使用 MySQL 8。Flyway 通过 `db/migration/mysql/V1__baseline_schema.sql` 建表，Hibernate 使用 `ddl-auto: validate` 检查实体与数据库是否一致。测试和显式的 `demo` profile 仍可使用 H2。

```sh
cp .env.prod.example .env.prod
# 编辑 .env.prod 后：
docker compose --env-file .env.prod -f compose.prod.yml up --build -d
```

`FileBasedChatMemory` 和 Kryo 依赖已移除。正式聊天记忆只以数据库这一条链路为准；不需要持久化的旧演示 ChatClient 仅使用进程内记忆，避免出现两套持久化真相。旧 Kryo 文件不自动导入，因为它们没有可验证的账号归属。

H2 存量数据的一次性搬迁、备份和回滚方法见 `docs/mysql-migration.md`。

## 边界和常见误解

- 摘要不是无损压缩；需要精确追溯时应查询原始 `chat_turns`。
- 当前的 token 数是保守估算，不是模型官方 tokenizer 的精确计数；预留预算就是为了吸收这个差异。
- 只有明确句式会进入结构化事实；自由文本提取、矛盾合并和记忆过期可作为后续增强。
- 跨请求恢复的是文本上下文；Manus 工具的执行现场和外部副作用不会自动续跑。
- 会话历史页面仍一次性读取全部消息；数据规模增大后应增加分页。

## 验证

```sh
./mvnw -Dtest='ConversationPersistenceTest,ConversationMemoryComponentsTest,ConversationControllerTest' test
PROD_ENV_FILE=.env.prod.example docker compose --env-file .env.prod.example -f compose.prod.yml config
```

覆盖点包括：短对话超过五轮仍可进入上下文、长对话不超 token 预算、滚动摘要、跨会话事实、跨用户隔离、事实删除、会话删除联动清理、磁盘重启恢复和 SSE 生成状态。

## 自测

1. 为什么两段同样是七轮的对话，进入模型的轮数可以不同？
2. 滚动摘要之后，原始聊天是否被删除？
3. 用户删除一段会话时，为什么要同时删除由它提取的事实？
