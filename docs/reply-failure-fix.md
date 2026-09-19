# “回复失败”的定位与修复

## 两个实际故障

1. 历史日志中，DashScope 查询 embedding 出现 `Connection reset`、`Unexpected end of file from server`。异常从 `LoveAppHybridDocumentRetriever.vectorSearch()` 传播到 RAG Advisor，导致 SSE 返回 error。修复前 BM25 没有机会接管。
2. 真实接口复测发现，当前 `qwen3.8-flash` 被发送到 `/api/v1/services/aigc/text-generation/generation`。服务端以 SSE error 返回 `InvalidParameter: url error`，旧版 SDK 在读取不存在的 `output.choices()` 时抛出空指针。阿里云文档说明该模型需要多模态接口：[文本生成接口适用范围](https://help.aliyun.com/zh/model-studio/text-generation)。

因此，仅调整前端提示或重启，无法完整解决问题。

## 修改

- 向量查询发生 `RestClientException` 时，继续执行本地 BM25；本次请求跳过其余扩展版本的向量查询，下次请求重新尝试。已有候选仍参与 RRF。
- BM25 无命中时，沿用允许空上下文的 Advisor，由聊天模型继续回答。这里只处理远端 HTTP 客户端异常，不吞掉本地编程错误。
- 默认、本地、生产配置均增加 `spring.ai.dashscope.chat.options.multi-model: ${DASHSCOPE_CHAT_MULTI_MODEL:true}`，保留 `qwen3.8-flash`，改用 `/api/v1/services/aigc/multimodal-generation/generation`。
- Manus 自建工具调用选项时继承模型的 `multiModel`，避免旧 SDK 默认的 false 覆盖正确配置。若以后换成只支持旧文本接口的模型，需要同时设置 `DASHSCOPE_CHAT_MULTI_MODEL=false`。

已有失败轮次保留其真实状态和已收到的内容。修复后的新请求继续正常保存会话；不会把失败轮次改成成功，也不会清空用户历史。

## 验证

相关回归共 41 项测试通过，0 失败、0 错误。

真实接口验证成功：收到 `ack → 97 个 delta → done`，数据库保存 514 字回答，状态为 `COMPLETED`。这次请求 ack 约 60 ms、正文首字约 22.8 秒；这是单次验证数据，不是性能基准。诊断账号及其会话已清理。

- 模拟日志里的连接重置和响应读取失败，验证 BM25 接管、空上下文、下一次请求恢复向量召回。
- 通过真实 RAG Advisor 和会话服务，验证出错情况下仍发送 `ack → delta → done`，保留历史上下文并写入完整回答。
- 用本地模拟 HTTP 响应检查当前 SDK 的实际请求路径及多模态文本解码，覆盖三个配置文件及智能体请求选项。
- 回归 SSE、缓存、会话持久化、权限和知识库维护测试。
- 本地真实聊天接口使用临时诊断账号验证，测试结束清理该账号及其测试会话。

复现测试不依赖外部模型；真实接口验证另行执行。降级解决向量检索依赖失败，不能保证聊天模型供应商完全断网或拒绝请求时仍能生成新答案。
