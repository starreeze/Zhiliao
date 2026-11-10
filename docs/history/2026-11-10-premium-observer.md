# 正文结构观察器准备与部署记录（已归档）

调查和临时诊断已于 2026-11-10 结束。当前手机安装的是生产版 `com.starreeze.betterzhiliao` 0.2（版本号 `202602032`），不是诊断模块；最终安装文件与 14:30 冻结的生产 APK 逐字节一致。原包已卸载，新模块仅对知乎 user 0 启用。原有 48 个设置完整保留，三个盐选过滤开关均关闭，悬浮广告和启动弹窗两个开关开启；手机诊断文件、私有 UI / 搜索记录与响应文件已清理，手机已安全锁定。旧模块 APK 和窄范围设置 / 作用域备份仍私密保留，用于回退。

正文调查的当前结论是：**所测正常原生响应只收到预览故事与解锁配置，并未收到门槛后的完整故事。** 完整 HTTP 窥视为 35588 字节；保存并核对的 JSON 是解析后重新序列化的 35337 字节快照，不能称为原始响应字节。其 156 个正文段落共 2787 字符，最后可见锚点仅命中一次且后续故事字符为零，末尾是 KM 付费截断卡；原生正文分页已结束。该结论只适用于实测账号和正常请求路径。

复查入口：

- [完整调查结论与分阶段证据](2026-11-10-premium-three-scenarios.md)，含失败边界、缓存时效、分类阳性 / 普通对照、推荐与搜索入口缺口。
- [所选响应的全字段脱敏核对](../evidence/2026-11-10-premium-observer/selected-response-evidence.json)及[样本比较矩阵](../evidence/2026-11-10-premium-observer/sample-comparison.json)。私有响应、锚点与故事文本不保留；[分析脚本与冻结观察器源码](../evidence/2026-11-10-premium-observer/README.md)用于复查方法。
- [推荐前后回调核对](../evidence/2026-11-10-premium-observer/recommendation-prefilter-summary.json)及[定向搜索的关联限制](../evidence/2026-11-10-premium-observer/targeted-search-summary.json)。推荐付费阳性未验证，产品采用用户接受的显式付费元数据保守规则，不额外请求详情。
- [生产版安装、独立开关与可见效果验证](../evidence/2026-11-10-betterzhiliao-v0.2/phone-validation.json)及[脱敏运行事件](../evidence/2026-11-10-betterzhiliao-v0.2/runtime-events.jsonl)。问题列表付费移除 / 普通保留、浏览集合移除与显式付费目标保留已实测；罕见广告无可用素材，用户允许跳过复现，未声称实际广告抑制已发生。全部过滤页等边界也未在实机复现。
- [CHANGELOG](../CHANGELOG.md)记录最终产品范围；此前 [正文交付调查](2026-11-09-premium-content-delivery.md)和[过滤可行性记录](2026-11-09-yanxuan-filter-feasibility.md)保留历史边界。

以下是诊断准备与运行期间的历史记录。“产品源码未改”“原模块已恢复”和后续待采样措辞均指各自当时的检查点；之后已完成推荐专用观察、产品迁移及生产版验证，不表示当前仍在运行诊断或计划继续采样。准备目录只保留静态 APK / 构建与 schema 材料，实际部署源码和脱敏证据已归档；其中临时路径不是当前手机状态的依据。

## 可复查产物

- 基线诊断 APK：`tmp/premium-observer-prep/BetterZhiliao_diagnostic.apk`。
- 完整节点聚合与独立续页观察修订：`tmp/premium-observer-prep/BetterZhiliao_diagnostic_segments.apk`；基线文件未替换。
- 隔离源码：`tmp/premium-observer-prep/source/`。相对主源码只新增 `hooks/PremiumObserver.java`，并在 `Hooks` 中注册该观察器。
- 构建日志及签名 / 包标识检查：`tmp/premium-observer-prep/build.log`、`artifact-verification.json`。
- **原机恢复 APK**：`backup/premium-observer-restore/BetterZhiliao_installed_restore.apk`。这是从本次离线快照中的已安装模块 APK 直接复制的恢复产物，位于 `tmp` 外；不要在诊断结束前删除。

最终 `assembleRelease` 成功。诊断和恢复 APK 均通过 `apksigner verify`，证书一致；包名均为 `com.shatyuka.zhiliao`，版本名 `0.1`、版本号 `202602031`，与已安装模块快照一致。证书私钥和口令未写入产物记录。没有执行额外正文请求或签名算法重建。

已用本次离线快照的知乎 APK 核对六组 hook 的目标方法与使用的 `ShortContent` / wrapper 字段。`hook_installed` 只在 `hookAllMethods` 返回非空集合时输出；类存在但方法缺失会记为 `hook_unavailable`，总准备信号同时报告完整 / 部分注册状态。注册成功仍不等于实际回调已经命中。

## 观察范围

保留现有模块逻辑，增加正常方法返回后的观察，不替换参数、结果、请求或成功 / 失败回调。

| 边界 | 观察内容 |
| --- | --- |
| `okhttp3.internal.http.RealInterceptorChain.proceed` | 限定回答详情、评论配置和 `next-render` / `simple-next-render`，从正常返回的响应使用 `peekBody` 取得有界副本；不消费原响应 |
| `service.short_container_service.dataflow.repo.c.c.a(JsonNode)` | 已选回答的解析输入结构与返回的 `ShortContent` 分类字段 |
| `service.short_container_service.dataflow.repo.b.a.a(ShortContent, boolean, boolean)` | 展平成 UI 节点后的数量、类名、展开状态和独立正文分页是否存在 |
| `comment.delegate.e.a(comment.a.a)` | `canReply`、提示长度及是否包含已知的精确付费提示；该模型无回答 ID，单独事件只能按受控 UI 操作时间关联 |
| `ZHObjectRegistryCenter$InnerDeserializer.deserialize`、`AnswerListWrapper.setupAnswerData` | 已选的旧 `Answer`、SDUI `Card`、问题回答列表中的元数据和出现位置集合 |

第三场景已经确认是原生 ShortContainer，故本次网络正文观察不假定旧 `AnswerAppView` HTML 路径会命中。离线 APK 另确认推荐 JSON 类型 `ComponentCard` 注册为 `ui.shared.sdui.model.Card`；其 `Card.Extra.contentId` 的 Jackson 名是 `extra.content_id`，`businessExtJson` 映射 `business_ext_map`。不能使用可复用的卡片顶层 ID 或业务扩展中含义不同的 `contentId` 代替回答 ID。旧模板 `ApiTemplateRoot` 不在这个诊断实现的元数据覆盖范围内；未命中不能反证页面没有盐选。

记录只包含回答关联 ID、类型、字符长度、数组 / 节点数量、显式权益 / 截断状态、分页存在性、已知标准提示的编号和探针上限标记。**不记录正文、原始 JSON、请求头、认证值、URL / 查询参数或异常消息。** 异常仅记录类别，观察器异常不得影响宿主网络和页面完成流程。

输出位于知乎私有目录 `files/.zhiliao-premium-observer.jsonl`。每个进程最多 120 个事件；累计文件最多 512 KiB；单次响应窥视最多 2 MiB，事件数或文件字节达到上限时输出限额标记。结构遍历限制深度 10、节点预算 240、每数组最多 40 项，列表元数据最多 80 项，均记录相应的探针上限。未识别字段计数，并输出最多 24 个只含英文字母 / 数字 / 下划线的安全字段名，不输出其值。达到响应窥视上限时不继续解析该不完整副本，不能据此说服务器截断了正文。

遍历已按实际静态富文本模型补入 `paragraph.text`、`heading`、`blockquote`、`reference_block`、`code_block.content`、`list_node.items`、`table.cells` 等路径，及原生 `structured_content.segments`。原始字段名和模型核对结果保存在准备目录的 `schema-verification.txt`、`rich-schema.txt`、`rich-beans.txt`；这些是静态 schema 证据，不包含用户响应正文。未知安全字段名用于判断是否需要修复观察范围，不能把未检查部分视作空正文。

`peekBody` 会提前缓冲有限数据，增加内存和等待开销；签名、请求内容和回调不变。它仍是尚待运行验证的观察方法，不是零影响保证。

## 选择样本与可选锚点

默认只选当前已识别的付费回答 `1977823141666501380`。可在首次启动前将额外公开回答 ID 写入知乎私有文件 `files/.zhiliao-observer-ids`，每行一个纯数字 ID，总长不超过 512 字符，最多 8 个不同 ID。选择器只在进程初始化时读取。

为了发现尚不知道 ID 的推荐阳性卡片，另启用有界的元数据发现：最多 32 条不同“模型类 + 回答 ID”记录，仅输出 ID、类型、业务子类型和标准付费标签 / 提示是否存在，不采集未知 ID 的正文。Card 的业务扩展只检查 `content_info` 的业务类型字段。候选值仍需界面验证；例如 `sub_type=normal` 或没有提示，不能单独作为已验证的普通负例。发现新 ID 后加入选择器并受控重载，再进行所选 ID 的正文结构采样。

标准提示编号为：0“精彩内容待解锁”、1“看广告免费解锁”、2“付费阅读内容仅盐选会员可评论”、3“选自盐选专栏”。元数据发现只报告这些已知文案的匹配编号，不记录其他标签或提示文本。

先完成分类样本：三个入口各一个明确盐选 / 付费样本和一个普通样本；另用一个普通但作者关闭评论的回答作为负例。必须在界面上确认内容与 ID 的关联，不能用历史列表最新一条猜测。推荐页没有阳性样本时明确记录缺口。

若分类回调正常，再考虑将**当前正文最后可见的一小段精确文本**放入私有文件 `files/.zhiliao-observer-anchor`（最多 256 字符）。观察器只在内存中比较，输出是否命中及同一文本字段中锚点后的字符数，不写出锚点或正文。一次只对一个目标使用锚点，完成后删除文件。该数值可能包含购买提示、版权或其他元数据，必须结合节点类型判断，不能直接等同于“付费全文在本地”。

## 恢复连接后的部署和恢复步骤

1. 复核手机 / 知乎 / 模块与本次快照一致；若模块已更新，重新备份当前实际 APK 并复核证书。保留原机恢复 APK。记录模块当前启用状态、知乎作用域和相关设置，避免更改现有过滤配置。
2. 在已授权的设备操作窗口，停止知乎进程，使用同签名覆盖安装诊断 APK（不卸载、不清除模块或知乎数据）。保留原 LSPosed 作用域，不额外启用其他模块。清理前次诊断日志，配置选定样本，再启动知乎，使代码和资源一起重新加载。
3. 先检查私有日志的 `ready`、`hook_installed`。出现 `hook_unavailable` / `observer_error` 先按精确类别判断；没有准备信号就不继续声称捕获有效。先做一个普通回答，再做已知付费回答，确认正常展开 / 收起和连续加载仍完成。
4. 在限定的正常 UI 操作中采样。不要点击广告解锁、购买或改动权益来制造结果。记录每次操作对应的入口、回答 ID 和可见门槛。收集日志时只保留诊断 JSONL，不混入认证或整段正文文件。
5. 停止知乎，用原机恢复 APK 同签名覆盖安装；删除诊断日志、ID 和锚点文件，再启动知乎。确认模块版本、启用范围、设置、标签和普通加载恢复。不要只替换 APK 而保留运行中的旧进程。
6. 设备操作结束时按 phone-control 流程安全锁定。诊断中若出现进程终止、持续加载不完成或新的反常行为，停止采样并恢复原模块，不重复尝试另一套注入方式。

覆盖安装和两次知乎进程重载会中断当前浏览位置及内存缓存。历史、账号、模块设置应随覆盖安装保留，但须实际复核；不需要手机重启。此前替换调试模块曾出现资源驻留问题，恢复检查必须包含可见标签。

## 判定标准

- **分类成功**：同一个已验证 ID 的正常响应 / 模型包含专用盐选标签、精确付费评论提示或经阳性 / 阴性比较确认的机器值；普通评论关闭负例不被等同为付费。六个入口样本分别记结果，未采到的不补作成功。
- **支持已收到门槛后故事数据**：未触及任何探针上限的目标响应中，能够把已知可见截止点、后续故事类型节点和原生渲染门槛对应起来；后续数据不能只是版权、广告、支付或导航节点。只有字符数增加不够。
- **支持本次请求只收到预览**：目标正常响应明确给出预览 / 截断状态，且已检查相关正文节点与正常展开 / 续页路径；结论只针对这些响应，不能推广为服务器永远不下发全文。
- **仍未定**：响应或节点没有命中、存在探针上限 / 未检查字段、仅有长度差、缓存与响应无法区分，或缺少“故事数据 / 元数据”的判别依据。不得把探针截断、网络错误、注入失败或界面门槛当作正文是否下发的证据。

## 手机运行检查点（本轮已恢复）

本轮先使用 `tmp/premium-observer-prep/BetterZhiliao_diagnostic_segments.apk`，后用独立 capture 修订核对单次 JSON；原基线 APK 均保持不变。曾使用的 `tmp/premium-observer-live/` 私有日志、截图、截止点锚点及手机诊断文件现已清理，仅保留脱敏证据。没有修改权益或点击广告、会员入口。

部署前确认原机模块与恢复 APK 字节一致，LSPosed 中本模块启用、知乎 user 0 作用域存在；原模块 48 个设置已私下保存用于恢复核对。第一版诊断正常回调六组 hook；修订 APK 覆盖安装后首次启动仍出现旧六组运行代码，尽管安装文件与修订相同。LSPosed 元数据路径已更新后，再次普通停止 / 启动知乎，取得新七组准备信号（含 `expanded_continuation` 与聚合扫描上限）。未修改 LSPosed 启用状态或作用域，未重启手机或杀全局 daemon。

已取得的正常路径证据：

- 控制回答 `99433728962`：真实详情网络响应 HTTP 200，`business_type=normal`、`can_reply=true`；评论 UI 为“理性发言，友善互动”。旧逐项探针只观察了 146 个 segments 中前 40 个，不能用它作整篇正文结论。
- 付费回答 `1977823141666501380`：真实详情网络响应 HTTP 200，`business_type=paid`、`can_reply=false`、精确评论提示编号 2。实际滚动至“精彩内容待解锁 / 看广告免费解锁 / 超级会员免广告”门槛，没有点击解锁。
- 修订聚合对该付费回答完整扫描 158 / 158 segments，无聚合上限：头卡 `km-paid-answer-header`、156 个 paragraph（2787 个识别故事字符）、末尾卡 `km-paid-answer-tail-truncate`。已知可见最后 28 字符的私有锚点在识别故事流末尾命中一次；其后没有识别故事字符。两份原生展开分页均 `is_end=true`；静态确认正常续页入口会在此条件下直接返回，不因 `next` 仍有值就发请求。

此后已核对头 / 尾卡的真实嵌套 JSON、全部根字段与字符串字段，完成问题列表付费 / 普通可见关联和独立普通长文对照。推荐仅做四屏自然试查，未取得阳性，保持缺口。原模块逐字节恢复，48 个设置、整个作用域和启用状态一致，普通加载与盐选标签实际可见，诊断文件逐项确认不存在。

## 离线复核：产物与原生裁剪边界

最终复制来源为隔离构建目录 `app/build/outputs/apk/release/BetterZhiliao_0.1.apk`。`cmp -s` 确认诊断 APK 与该构建输出逐字节相同，与原机恢复 APK 不同。两者文件大小恰好都是 134860 字节，不能因此判断相同；最终诊断 DEX 解压后 92416 字节，恢复 DEX 为 76768 字节。编译后的 `Hooks.<clinit>` 首先实例化 R8 映射后的 `PremiumObserver`；修订后的 schema、发现上限、事件上限、裁剪参数标记均存在，恢复 APK 没有这些标记。两者签名证书的完整 PEM 比较相同，`apksigner verify` 通过。详细结果保存在准备目录的 `artifact-revalidation.json` 和 `compiled-registration.txt`，未用哈希代替字节比较。

知乎 11.5.0 的 `classes14.dex` 中，`service.short_container_service.dataflow.repo.b.a.a(ShortContent, boolean, boolean)` 的第一个布尔参数为真时调用 `IDataProvider.cutoutContent(uiNodes, content)`；为假时设置 wrapper 展开状态，并逐个解析 `structured_content.segments`。观察事件因此记录 `cutout_requested` 和未额外解释语义的 `render_arg2`。它们是实际调用参数，不是服务器付费状态。

`classes10.dex` 中 `ShortContainerDataProvider.cutoutContent` 委托给 `feature.short_container_feature.dataflow.c.b.a(ArrayList, ShortContent)`。后者测量收到的 segment 视图，按高度构造 `CutoutNode(nodeBean,index,showHeight)`，并计算 `showMaxLine`、`showMask`、`expandSingleLine` 和 segment 边界。**这是普通收起 / 展开也会使用的布局裁剪。** `ShortContent.cutoutMinHeight`、`cutoutMaxHeight` 的实际 Jackson 注解是忽略字段（`com.fasterxml.jackson.a.o` / `JsonIgnore`），并非已证实的服务器 `cutout_min_height` / `cutout_max_height` 字段。观察器从模型 getter 记录真实运行值；原始结构中的同名候选字段即使出现，也须单独解释来源。

付费专栏尾部有另一条明确的原生 gate 渲染映射：

```text
segment.type=card / card.card_type=paid_column_tail_truncate
  -> feature.column_feature.shortcontainer.provider.ZRichViewHolderProvider.parseData
  -> feature.column_feature.model.ColumnPaidCutoutUINode
  -> PaidColumnCutoutView.setData
```

该 gate 模型的 JSON 字段为 `column_token`、`content_module`、`text_link`；View 用它们设置购买入口、作者和专栏说明，不是故事正文。原始结构观察现在会输出受限的 `card_type`、`richType` 类型值，便于把实际 gate 节点与 paragraph / heading / list 等正文节点分开。该静态路径尚不证明当前目标回答使用了此节点。`reward_tail_truncate` 则明确注册到 `zrich.giftzrich.GiftImpl`，其模型继承 `RewardInfo`、渲染打赏相关视图；不能凭名称把它认定为实测的“看广告免费解锁”门槛。

正文还可以独立分页：裁剪高度准备路径在已收到节点高度不足且 `paging.isEnd=false` 时，跟随 `paging.next` 正常获取下一批 `segments`，读取新 `paging`；因此“首页未出现后文”不等于后文不会通过正常展开 / 续页收到。观察器同时记录 wrapper 的 `expandedContentNodePaging`、`expandedContentNodeTmpPaging` 是否存在、`isEnd` 和 next 是否存在，绝不输出 next URL。

交付判据据此细化：先对应同一回答的收起参数、展开参数和独立正文分页，再在收到的 `segments` 中定位实际 gate 类型及可见锚点；锚点之后只有购买说明、打赏、版权或导航节点不计作后续故事。普通布局的 `CutoutNode` 或模型高度本身不能证明付费全文被隐藏。当前网络观察还可能略过没有回答 ID 的 continuation 响应，缺少这些页时不得给出“仅预览”或“已含全文”的结论。

### 已核实的 ID 缺失续页观察入口（已加入聚合诊断构建）

`classes14.dex` 的私有实例方法是：

```text
service.short_container_service.dataflow.repo.c.a(
    ShortContent original,
    JsonNode receivedPage,
    service.short_container_service.a.a telemetry
) -> java.util.List
```

静态调用链为 `repo.c.a(ShortContent)` → 读取 `original.wrapper.getPaging().getNext()` → `repo.d.a.a(nextUrl)` → Retrofit `repo.a.c.b(String,String)` 的动态 GET → `repo.c$h.a(JsonNode)` → 上述私有方法。`c$h.c` 保存原始 ShortContent；无需从正文重新猜 ID。该方法调用 `repo.c.e.a(receivedPage)`，解析根部 `segments` 与 `paging`，用新分页更新原模型 wrapper，再返回解析后的节点集合。server 提供的 next URL 原样传入请求；这里没有可以静态保证的固定 continuation pathname。

若现有构建确认漏掉 ID 缺失页，最小修订是在这个私有方法正常返回后追加一个所选 ID 限定的观察：结构化第二参数、返回节点类 / 数量及同一模型更新后的分页；不改 Observable、请求、成功回调或原结果，也不输出 next URL。必须按三参数确切类型过滤，排除四参数静态转发方法以免重复。该观察已加入独立的聚合诊断 APK，第七组 `expanded_continuation`；部署及实际回调验证由实时采集任务顺序协调。裁剪高度准备里的同步续页走另一条路径，此 hook 未自动覆盖它，不能隐去这个范围限制。


## 完整节点聚合修订交付

基线采样确认首 40 节点的补充 shape 不能覆盖普通回答的 146 个 segment 和付费回答的 158 个 segment，因此构建独立的 `BetterZhiliao_diagnostic_segments.apk`。构建成功，`cmp` 与最终 Gradle release 输出相同、与原机恢复 APK 不同；签名证书匹配，编译注册和新增 aggregate / continuation 标记均验证通过。文件仍为 134860 字节，DEX 为 97984 字节；复核结果为准备目录 `artifact-revalidation-segments.json`。没有覆盖先前基线文件，主产品源码未改。

新增 `received_segments_aggregate` 与旧 shape 的 40 项、240 节点限制独立。它扫描 `structured_content.segments` 或续页根部 `segments`，上限为 4096 个 segment、65536 个递归节点、2 MiB 字符总数、深度 20，明确记录 `scan_complete` 和各 cap。输出按实际类型聚合的节点数与字符数、最后 12 个节点的顺序摘要、最多 32 个已知 gate 的位置及前后故事字符数；类型名最多 48 桶。渲染后 `render_aggregate` 也独立统计最多 4096 个节点类和末尾顺序。旧 shape 出现 cap 并不自动使独立 aggregate 不完整，须分别阅读。

故事字符只计已识别的 paragraph / heading / quote / code / list / table 等类型中已知文本叶子，排除 marks；HTML 文本在内存中归一化，按原节点顺序无分隔符拼接。购买尾卡不计故事。其他 segment 和未展开的 JSON 字符串单列统计，不能凭 `scan_complete=true` 宣称所有内容都是故事或服务器已经下发全文。可选锚点只输出故事序列中的匹配次数、偏移及其后的故事字符数（最多 8 个位置），无锚点文本、正文、链接、查询或认证信息。跨节点排版中的换行和空白差异可能导致不匹配。

静态再次确认 `feature.short_container_feature.ui.b.b.a(ShortContent)` 在 `paging.isEnd=true` 时直接返回，只有 false 才跟随 next。因此 `is_end=true` 同时 `next_present=true` 并不意味着还需请求；观察仅等待应用正常 UI 路径，不手工获取 next，不修改结束标记。

修订后来已取得七组实际准备信号与完整聚合；本次回答的交付结论记录在最终调查文档及脱敏 JSON 中。

### 聚合构建部署后的加载核对

采集负责人报告覆盖安装与冷启动后仍输出基线六组 hook，ready 也缺少新增 aggregate 上限字段。重新取得的已安装 APK 与聚合候选文件经 `cmp` 确认逐字节相同，单 DEX 为 97984 字节。候选 DEX 的实际 hook 方法含七次顺序安装调用，明确包含 `expanded_continuation`；ready 无条件写入三个 aggregate 上限字段。基线 APK 的 DEX 为 92416 字节，且与候选文件不同。

因此当时运行日志对应的执行代码与已安装候选不一致，不能把六组事件视为聚合构建成功生效，也不能归因于候选缺少第七次调用或普通 method_missing。随后确认 LSPosed 记录的 APK 路径已更新，再做一次普通知乎停止 / 启动后得到七组准备信号。没有为此重复构建、切换模块开关、杀全局 daemon 或重启手机；不把未经确认的底层缓存原因写成结论。

## KM 卡片静态映射与单次私有响应采集

实际阳性目标的卡片类型是 `km-paid-answer-header` 和 `km-paid-answer-tail-truncate`，不能套用此前另一路 `paid_column_tail_truncate` 计数。知乎 `classes16.dex` 的 `vipchannel.zrichimpl.ZRichViewImpl.parseData` 分别把它们映射到 `vipchannel.model.PaidHeaderData` 与 `vipchannel.model.TruncateInfo`。header 用 `HeaderInfoContainerView.setData` 渲染；truncate 交给 `vipchannel.view.c` 按 `style` 选择视图并绑定。

`PaidHeaderData` 的字段包括作者 / 标题 / 头部说明、版权、业务和 SKU 标识、VIP 状态及 ZA 元数据。`TruncateInfo` 的显式字段包括 `authentication_result`、回答 / 内容 / 业务标识、scene/style、text / 颜色 / 图片 / URL 和 `dynamics_card`。后者的 `DynamicCardInfo` 字段为按钮 / 副标题 / 截断说明、价格 / 优惠券、跳转地址、广告数据等；未声明 story、正文或 segments 容器。静态 schema 支持其作为头部和 gate 元数据的用途，但不能仅凭模型排除实际响应的未知字段，因此准备一次最窄的私有原始 JSON 核对。

独立产物为 `tmp/premium-observer-prep/BetterZhiliao_diagnostic_capture.apk`，没有覆盖基线或聚合文件。构建与字节比较通过，签名同恢复 APK；DEX 99024 字节。ready 另输出 `private_capture_protocol=exact_selected_detail_once` 和 `private_capture_enabled`，用于验证实际加载的是这一修订，不能仅凭七组 hook 判断。

只有初始化前存在知乎私有触发文件 `files/.zhiliao-observer-capture-once` 才启用。捕获严格限定：回答详情端点关联 ID 为 `1977823141666501380`、该 ID 在样本白名单、HTTP 200、JSON Content-Type、完整窥视少于 2 MiB、有效 JSON 对象且根部 `id` 精确匹配；规范化后的 JSON 也须少于 2 MiB。它不读取认证头、不额外发请求、不改变权益或回调。

唯一输出文件为知乎私有 `files/.zhiliao-observer-response.json`，创建后先通过 `Os.chmod(...,0600)` 设为仅属主可读写，再写入已经解析的响应 JSON。文件存在时不覆盖；成功后本进程停用采集。JSONL 仅写 `private_capture_saved` 的 ID / 状态 / 字节数，不写正文或未知字段值。触发文件和原始文件不纳入持久文档或仓库。采集负责人拉取后应保持本地文件 0600，只做离线结构 / 不透明 JSON 字符串 / 正文截止点核对，不在工具输出打印全文或私有 URL。

核对完成立即删除设备和本地原始 JSON、触发文件与锚点，并恢复原机模块；仅保留去敏的结构统计及结论。若没有 `private_capture_saved` 或实际 JSON / ID 校验失败，明确记录没有取得目标响应，不扩展到其他 ID 或整个网络流量。

## 推荐流缺口与元数据专用修订

推荐采样记录包含 21 个 SDUI Card（`content_type=answer`）和 6 个 ShortContent（pin）；没有事件 / 日志上限事件，最大 sequence 为 44。21 个 Card 记录没有 `feed_business_type`、`feed_sub_type` 或精确标签字段，顶层 `business_type/sub_type` 为 null。这并不证明推荐页没有付费内容：旧观察器根本没有处理 `PaidContent`、`MomentsPaidColumn`、`MomentsManuscript` 或 `ApiTemplateRoot`，而 MarketCard 的 extra 只有 id/type，旧观察器按 extra.contentId 取 ID 也会遗漏它。

另有两个明确的观察偏差。其一，旧观察器和 CustomFilter 都在同一个 InnerDeserializer 的默认优先级 after 回调中工作；CustomFilter 可把结果置 null，注册位置在前并不能保证观察到未过滤对象。采样时 mainswitch、marketcard、feedad、removearticle 均为 true；pin/video 为 false。其二，Card.Extra 的确切泛型是 `businessExtJson/customInfo = Map<String, ? extends JsonNode>`，并非所有子项仍是 Java Map；scm 才是 `Map<String,String>`。旧嵌套字段辅助函数没有按 JsonNode 解读，故它没取到业务字段不能作为字段不存在的证明。

为此构建独立的 `tmp/premium-observer-prep/BetterZhiliao_diagnostic_recommendation.apk`。它只注册 `RecommendationObserver`，不注册 PremiumObserver；R8 已移除未使用的私有正文采集代码标记。源码和最小 Hooks diff 冻结在准备目录 `frozen-recommendation-source/`，之前已部署的 capture 源码冻结副本未变。最终 isolated release 构建成功，`cmp` 与该输出相同、与恢复 APK 不同，签名证书相同，DEX 86908 字节；检查记录为 `artifact-revalidation-recommendation.json`。

新 ready 事件为 `recommendation_metadata_ready`，protocol 为 `prefilter_metadata_with_priority_receipt`，必须验证两个回调方法集合非空。LOWEST 的 after 回调保存**已去敏元数据**，HIGHEST 的 before 为同一调用分配编号，HIGHEST 的 after 输出同编号 receipt：实际是否先看到 LOWEST、结果后来是否被其他回调置 null、返回类是否变化。它从不改结果或参数，不改过滤设置；仅通过 callback param extras 在一次调用中传递去敏 JSON。低 / 高回调的实际先后仍要由运行日志验证，本地 API82 JAR 只是 Stub，不能拿它冒充已验证的 LSPosed 运行实现。

覆盖的模型为 Answer、SDUI Card、PaidContent、MomentsPaidColumn、MomentsManuscript、ApiTemplateRoot、MarketCard。PaidContent 的本类和 ZHObject 父类没有 ID 字段，因此明确记录 ID 不可用，不解析或输出 URL 来猜 ID。MomentsManuscript 保留实际 type 区分 paid_column_manuscript 与 ebook / album 等，不能按整个类判盐选；MarketCard 明确标成会员营销家族，不能当付费回答。Card 按 `extra.content_id` 关联，正确遍历 Map→JsonNode，输出有限 schema 名和白名单业务 / 子类型 / 布尔值；元素树只在内存匹配精确标签编号 0“选自盐选专栏”、1“付费阅读内容仅盐选会员可评论”，不输出标题、作者、摘要或正文。

输出为知乎私有 `files/.zhiliao-recommendation-metadata.jsonl`；最多 64 个去重元数据记录、256 事件、512 KiB，每个 Card 元数据树最多 128 节点 / 深度 3，元素树最多 256 节点 / 深度 8，单文本匹配最多 16384 字符。上限均明确标记；未知模型类只记录最多 16 种类名。没有网络窥视、原始响应文件或正文采集触发器。后续采集先清该私有日志，在受控推荐窗口验证 ready、配对先后及实际阳性；全局反序列化也可包含预加载对象，记录数不能冒充可见卡片数。未发现实际付费对象时仍保留推荐阳性缺口。

准备中曾误在主 checkout 执行一次 assembleRelease，重新生成了被忽略的主构建输出；产品源码和设备没有修改，恢复文件未动。随后已使用隔离目录重新构建并核对最终候选；误生成的 checkout 外 task 日志已删除。
