# 管理员知识库维护

访问 `/admin/knowledge`，或使用顶部导航的“知识库”入口。只有管理员可以看到入口、进入页面及调用维护接口。普通用户仍可以正常使用情感问答，管理权限不会开放给普通账号。

## 已实现

- 上传 UTF-8 编码的 Markdown（`.md`）或纯文本（`.txt`），每份不超过 512 KB、200000 字符。
- 搜索文档标题/文件名，查看正文，修改标题和正文。
- 删除确认；未保存的编辑在切换文档、离开页面时提示。
- 保存失败保留编辑草稿；多个管理员修改同一文档时使用版本号检测冲突。
- 文档保存在与用户和历史会话共用的数据源中；正常运行使用 MySQL，测试可使用 H2。
- 项目自带的 `src/main/resources/document/*.md` 首次启动自动导入，可在页面中修改或删除。
- 修改与删除同步更新向量检索、BM25 关键词检索。历史情感聊天入口已接入这套 RAG 知识检索，同时继续携带原有会话历史。

当前范围是可在线编辑的文本知识文档；PDF、Word、图片、OCR 和批量导入不在本次范围。

## 权限

前端导航使用 `isAdmin()` 控制可见性；路由声明 `requiresAdmin`，进入管理页前重新调用 `/auth/me` 校验服务端最新身份。

所有维护接口都位于 `/admin/knowledge/documents` 下，由现有 Spring Security 的 `/admin/** -> hasRole("ADMIN")` 规则保护。JWT 过滤器依据数据库中最新的角色和启用状态建立身份，不信任请求正文中的 userId，也不单独依赖 token 内的角色声明。

匿名访问返回 401，普通账号返回 403。前端隐藏只是交互层限制，实际权限由后端保证。

## 数据与索引更新流程

`knowledge_documents` 表保存：UUID、乐观锁版本号、标题、原始文件名、完整正文、是否内置、删除标志、最后修改管理员、创建与更新时间。

文件直接解析为文本入库，不将用户提供的文件名拼接为磁盘路径。上传会检查扩展名、UTF-8、空内容、大小和二进制控制字符。

每次管理操作执行以下流程：

1. 检查文件或编辑参数、文档是否存在、提交版本是否匹配。
2. 读取当前文档集合，生成候选文档集。
3. 按约 1000 字符、100 字符重叠切片，使用稳定切片 ID；只为新增或变化的切片调用 embedding。
4. 构建候选向量索引和关键词索引。此时正常问答仍使用旧的完整快照。
5. 候选索引成功后，提交数据库短事务；事务成功后原子发布包含两种索引的新快照。
6. 返回操作成功。之后开始的检索会使用新知识。

索引生成失败返回 503，数据库和现有索引保持原状态。数据库提交失败也不会发布候选索引。删除操作复用其他文档的已有向量，因此不需要重新调用 embedding。

默认文档使用由文件名生成的稳定 ID，删除后保留删除标记，避免重启时重新导入。数据库是导入后的权威来源，后续修改 classpath 同名 Markdown 不会覆盖后台编辑。重启会从数据库重新构建内存索引；数据库文件需要像历史会话一样保留/挂载。

`KnowledgeIndex` 发布一份包含向量库和关键词语料的快照，`LoveAppHybridDocumentRetriever` 在检索开始时固定使用同一份快照。管理更新不会让一次检索读到一半新、一半旧的索引。更新前已经开始的检索仍可能使用旧快照。

没有知识文档时，RAG 允许空上下文，情感聊天仍可继续。删除文档不会改写已经保存的聊天记录；旧回答仍然可能存在于原会话的上下文中。Manus 保持既有工具流程，本次维护的是项目原有的情感知识库。

## 接口

路径均相对于 `/api`，需要管理员 Bearer token。

| 方法 | 路径 | 请求 / 返回 |
| --- | --- | --- |
| GET | `/admin/knowledge/documents` | 文档摘要列表，不返回全文 |
| GET | `/admin/knowledge/documents/{id}` | 完整文档、正文和版本号 |
| POST | `/admin/knowledge/documents` | multipart 的 `file` 字段；成功 201 |
| PUT | `/admin/knowledge/documents/{id}` | JSON：`version`、`title`、`content` |
| DELETE | `/admin/knowledge/documents/{id}?version=0` | 携带当前版本；成功 204 |

参数错误返回 400；文档不存在/已删除返回 404；版本冲突返回 409；索引构建失败返回 503。

目前列表一次性返回摘要，前端本地筛选；管理操作在单个应用实例内串行执行。多实例部署需要增加共享索引和变更广播/分布式协调，不能直接以多份内存索引运行。文档较大时更新索引需要等待，页面会显示进行中状态。

## 关键代码

- `knowledge/KnowledgeDocumentStore.java`：持久化、默认文档导入、版本检查、逻辑删除。
- `knowledge/KnowledgeManagementService.java`：候选索引、事务提交和发布顺序。
- `knowledge/KnowledgeIndex.java`：向量复用、完整索引快照。
- `knowledge/KnowledgeValidation.java`：上传和正文校验。
- `knowledge/KnowledgeController.java`：管理员 REST API。
- `rag/LoveAppHybridDocumentRetriever.java`：读取同一版本的索引快照。
- `app/LoveApp.java`：会话历史与 RAG 同时用于流式问答。
- `liu-ai-agent-frontend/src/views/AdminKnowledge.vue`：管理界面。

## 验证

```sh
mvn -o -Dtest='KnowledgeManagementTest,KnowledgeSecurityTest,LoveAppHybridDocumentRetrieverTest,ConversationPersistenceTest,ConversationControllerTest,LoveHistoryPromptTest,ConversationSecurityStreamTest,AuthServiceTest' test
npm --prefix liu-ai-agent-frontend run build
```

新增 10 项后端测试覆盖真实 H2 持久化和重启、增删改同步双索引、向量复用、索引失败回滚、版本冲突、上传校验、空库、知识进入实际模型 Prompt、全部维护接口的管理员权限和错误响应。回归已有检索、历史对话及登录测试。

浏览器脚本使用模拟 API，不改动真实账号/文档或调用付费模型：

```sh
PLAYWRIGHT_MODULE=/path/to/playwright node scripts/knowledge-ui-smoke.cjs
```

它覆盖上传、保存、刷新恢复、删除确认与取消、搜索、失败保留草稿、移动端溢出，以及普通用户不可见和直接访问管理地址被拦截。
