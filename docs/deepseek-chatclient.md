# DeepSeek ChatClient 配置

当前项目已支持通过 Spring AI OpenAI-compatible 客户端连接 DeepSeek。

默认不启用真实模型，避免测试和本地开发误触发网络请求。启动时加：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run \
  -Dspring-boot.run.arguments="--agent.deepseek.enabled=true --agent.deepseek.api-key-file=/path/to/deepseek-apiKey.txt"
```

配置位置：

```yaml
agent:
  deepseek:
    enabled: false
    base-url: https://api.deepseek.com
    api-key-file: ${DEEPSEEK_API_KEY_FILE:}
    model: deepseek-v4-flash
```

API key 从 `api-key-file` 读取，不写入仓库。

启用后，`POST /api/agent/chat` 会优先走真实 DeepSeek `ChatClient`：

- 知识库检索结果会作为可引用上下文传给模型。
- 业务查询仍通过 `LogisticsTools` 暴露的 Spring AI `@Tool` 执行。
- 权限上下文通过 `toolContext` 传入工具。
- 如果 DeepSeek 调用失败，会自动回退到本地规则编排。

`POST /api/agent/customer-diagnosis` 的业务事实、异常归因和 SLA/赔付候选由后端工具先生成结构化证据；启用 DeepSeek 后，模型会基于这些证据生成诊断叙述。如果 DeepSeek 失败，接口会回退到本地结构化诊断叙述。
