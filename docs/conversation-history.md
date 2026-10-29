# 历史对话与 Agent Memory

两个 AI 入口共用持久会话、按 token 预算组装上下文、关系分类摘要、当前会话历史召回和用户明确表达的结构化事实。恋爱问答还在 RAG 增强后执行最终 Prompt 预算校验。

## 小白版：三层笔记

可以把记忆理解成三层：

1. **原始聊天记录**：完整保存，用于页面回看和审计。
2. **会话摘要与历史召回**：对话太长时，把较早的用户陈述分类整理；再次提到旧事件时，检索原始记录中的相关片段。最近内容仍保留原文。
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

当未摘要的完整轮次超过原文目标预算时，系统将较早轮次合并到滚动摘要，至少保留最新一轮作为原文候选。是否真正携带该轮仍取决于剩余输入预算；摘要不会覆盖或删除原始 `chat_turns`。

### 关系分类摘要

`RelationshipSummary` 扫描用户消息中的句子，按“关系背景、关键事件、当前困扰、用户目标、沟通反馈、其他陈述”归类。每类保留最近三条，并标注来源轮次。这样长消息末尾的“我想先修复关系”也有机会保留，不再只截取消息开头 240 个字符。

```text
- 【关系背景】[turn=12] 用户曾说：我们异地两年
- 【关键事件】[turn=15] 用户曾说：上周因为失约吵架
- 【用户目标】[turn=18] 用户曾说：我想先修复关系
- 【沟通反馈】[turn=20] 用户曾说：尝试谈心后好转
```

这是本地规则驱动的抽取式摘要，不额外调用大模型，也不声称能理解所有表达方式。它保留用户原话而非生成新的事实；助手旧回答不会混入。各类别优先保留最新陈述，注入时均分首条笔记的空间，避免一个类别挤掉所有其他类别。旧状态保留来源轮次，提示模型不要当作当前状态；不进行自动矛盾裁决。旧版纯文本摘要可继续读取。

### 相关历史召回

`ConversationHistoryRecall` 使用当前问题中的领域词及中文双字片段，检索当前账号、当前会话中已完成且已压缩的旧轮次。最多八个关键词，每词最多十二条候选，按匹配词数及轮次排序，返回最多三条有来源的用户原话片段；截取从命中位置附近开始，避免只读取长消息开头。

召回使用数据库文本过滤，不要求 embedding 可用，不扫描前端已加载的消息。即使某事件已被摘要淘汰，仍可命中原始历史。候选仅来自当前会话；姓名、偏好等已有显式事实机制继续跨会话使用。记忆预算内部为事实和召回分别最多预分配约三分之一，其余空间供分类摘要使用，总量仍受 `memory-token-budget` 限制。

### 输入与最终请求预算

1. 保存新轮次之前检查当前消息的估算 token 数；超过“输入预算减预留预算”时返回 HTTP 400，提示分段发送，不留下生成中的空轮次或事实记忆。
2. RAG 的来源片段合计受 `rag-context-token-budget` 限制；资料标签与来源编号保留，放不下任何来源时使用无资料回应规则。
3. `FinalPromptBudgetAdvisor` 排在 RAG 之后，对即将发给模型的全部文本消息重新计数。超限时先撤下本次检索资料并明确不得编造引用，再从最旧的完整问答开始裁剪，仍不够时撤下派生记忆。
4. 系统规则和当前用户完整原文始终保留；必要内容仍放不下则拒绝调用模型，不静默截断用户问题。最终限制为 `model-input-token-budget - final-token-safety-margin`。

此校验使用项目估算器，不能等同于模型官方 tokenizer 的精确计数。输入预算还应根据实际模型窗口设置，并给输出单独留出空间；本功能只约束恋爱问答的最终文本输入，不改变 Manus 工具调用现场。

事实提取仅接受受支持的第一人称明确句式（姓名、专业、过敏、喜欢、不喜欢），不保存模型推测。事实会绑定来源；删除来源会话或超期清理时，相关派生记忆一并删除。记忆被包在标记为“非可信历史数据”的系统消息中，当前用户指令冲突时以当前指令为准。

## API

以下路径相对于 `/api`，均使用当前 JWT 账号：

| 方法与路径 | 作用 |
| --- | --- |
| `POST /ai/conversations` | 新建 LOVE 或 MANUS 会话 |
| `GET /ai/conversations?appType=LOVE` | 获取当前用户会话 |
| `GET /ai/conversations/{id}?size=50&beforeTurnId=123` | 按完整轮次分页回看历史 |
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
      final-token-safety-margin: 256
      rag-context-token-budget: 2500
      reserved-token-budget: 4000
      memory-token-budget: 2000
      raw-recent-token-target: 5000
      max-candidate-turns: 100
      max-summary-chars: 6000
      max-facts-in-context: 50
```

对应环境变量是 `CHAT_MEMORY_MODEL_INPUT_TOKEN_BUDGET`、`CHAT_MEMORY_RESERVED_TOKEN_BUDGET`、`CHAT_MEMORY_TOKEN_BUDGET`、`CHAT_MEMORY_RAW_RECENT_TOKEN_TARGET`、`CHAT_MEMORY_MAX_CANDIDATE_TURNS`、`CHAT_MEMORY_MAX_SUMMARY_CHARS`、`CHAT_MEMORY_MAX_FACTS_IN_CONTEXT`、`CHAT_MEMORY_FINAL_TOKEN_SAFETY_MARGIN` 和 `CHAT_MEMORY_RAG_CONTEXT_TOKEN_BUDGET`。

## 历史分页与前端同步

历史接口默认返回最近 50 轮（100 条消息），`size` 范围是 1～100 轮，`beforeTurnId` 为独占上界。返回的 `messages` 仍按时间正序排列，另含 `hasMore` 与 `nextBeforeTurnId`。下一页请求使用前一页的游标，新回复插入时不会像 offset 分页一样让旧页漂移。失败和中断的轮次也可回看，每轮的用户和助手消息一起返回。

```json
{
  "conversation": { "id": "…" },
  "messages": [],
  "hasMore": true,
  "nextBeforeTurnId": 123
}
```

前端提供“查看更早的记录”，按持久化消息 ID 合并、去重和排序；加载旧页时以原有消息为滚动锚点。回复完成或轮询最新状态时合并最新页，保留已经展开的旧页。本次变更将历史详情从一次性全量读取改为默认分页，其他客户端也需要按游标继续取页。会话列表目前仍全量返回。

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
- 分类依赖关键词规则，历史召回依赖词面匹配；同义改写、没有具体关键词的指代不能保证找回。
- 旧摘要会在后续发生压缩时逐步转换；没有数据库结构变更，无需新增迁移脚本。

## 验证

```sh
./mvnw -o -Dtest='Conversation*Test,RelationshipSummaryTest,FinalPromptBudgetTest,LoveHistoryPromptTest,LoveAppContextualQueryAugmenterTest' test
(cd liu-ai-agent-frontend && npm test && npm run build)
PROD_ENV_FILE=.env.prod.example docker compose --env-file .env.prod.example -f compose.prod.yml config
```

覆盖点包括：分类摘要与来源、长消息末尾目标、旧版摘要兼容、旧事件原话召回、跨用户和跨会话隔离、超长输入无副作用、RAG 后最终 Prompt 预算、完整问答裁剪、游标分页及插入稳定性、前端旧页合并和去重，以及原有磁盘重启恢复与 SSE 状态。测试使用模拟模型，不依赖模型 API 调用。

## 自测

1. 为什么两段同样是七轮的对话，进入模型的轮数可以不同？
2. 滚动摘要之后，原始聊天是否被删除？
3. 用户删除一段会话时，为什么要同时删除由它提取的事实？
