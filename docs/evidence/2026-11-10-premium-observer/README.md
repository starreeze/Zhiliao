# 正常客户端诊断证据

这些文件只含字段路径、类型、长度、已知提示编号、公开回答 ID 和恢复检查，不含故事、认证值、URL、原始响应或截图。

- `selected-response-evidence.json`：选定回答的完整已接收 JSON 解析快照检查结果。HTTP body 窥视为 35588 字节；解析后重新序列化快照为 35337 字节，两者不能混称原始响应大小。
- `analyze_selected_response.py`：本次实际使用的离线检查方法。输入是私有解析快照、可见锚点及诊断元数据；这些私有输入现已删除，仓库不提供正文重放。
- `sample-comparison.json`：问题列表、原生详情 / 折叠态对照及最终恢复状态。
- `recommendation-discovery.json`：四屏推荐观察期间的 21 条 Card 记录，包含后台预取；其缺失业务值不能当作普通回答或完整覆盖的证明。
- `observer-source/`：与实际部署的 `BetterZhiliao_diagnostic_capture.apk` 对应的冻结源码和注册差异，不包含后续推荐探针修改。依赖原项目构建配置；签名密钥、账号数据和输入内容不随证据保存。

标准提示编号：0“精彩内容待解锁”，1“看广告免费解锁”，2“付费阅读内容仅盐选会员可评论”，3“选自盐选专栏”。

最终解释与限制见 [调查文档](../../history/2026-11-10-premium-three-scenarios.md#正常客户端实测结论与恢复)。

推荐补充探针另保存 `recommendation-prefilter-summary.json`、`recommendation-prefilter-events.jsonl` 和 `recommendation-observer-source/`。它只采集元数据；22 对 LOWEST / HIGHEST 收据验证了实际回调顺序，4 个被移除对象均是 article，不能标为付费。其标签编号与正文观察器不同：0“选自盐选专栏”，1“付费阅读内容仅盐选会员可评论”。
