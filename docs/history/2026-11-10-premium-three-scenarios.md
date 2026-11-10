# 盐选过滤的三个入口调查

已取得知乎 11.5.0 正常客户端的 HTTP 200 正文数据。针对回答 `1977823141666501380`、当前账号及本次原生请求路径，收到的故事数据只有可见的 2787 字符预览和末尾解锁配置；没有取得门槛后的故事正文。这个结果支持本次只下发预览，而不是完整故事已经下发后仅由显示层隐藏。结论不推广到所有账号、回答或后续请求。

问题回答列表与打开后的原生 ShortContainer 已取得付费 / 普通对照，`business_type=paid/normal` 与实际标签、页面对应。推荐页仍未取得有效付费阳性，探针覆盖和既有过滤开关也存在限制；不能声称三个入口全部验证完成。用户随后接受了显式付费元数据保守规则，后续生产版范围及独立开关验证见 [0.2 实机证据](../evidence/2026-11-10-betterzhiliao-v0.2/phone-validation.json)。

本文保留前期静态分析、失败捕获和离线检查作为历史过程。下文早期阶段中的“仍未定 / 尚未取得”指当时状态；正文运行结果、证据文件和原版恢复检查见末节“正常客户端实测结论与恢复”。该恢复是诊断结束时的检查点，之后已按用户新要求迁移至生产版 0.2；当前安装、清理与安全锁定状态见[已归档观察器记录](2026-11-10-premium-observer.md)和 [CHANGELOG](../CHANGELOG.md)。

## 静态分析：分类依据与第三场景

### 三个入口不能共用未经验证的页面假设

| 场景 | 已确认的结构或入口 | 当前边界 |
| --- | --- | --- |
| 推荐页 | 模块 `CustomFilter` 处理 `ApiTemplateRoot`、SDUI `Card` 和 `MarketCard` | 会员推荐卡不等于全部盐选；尚缺推荐页盐选阳性模型 |
| 问题回答列表 | `AnswerListWrapper.setupAnswerData(IAnswerList, boolean)`；回答可直接存在或由 `MixShortCardTargetWrapper` 包装 | 先前确认 `Answer.labelInfo.text` 到“选自盐选专栏”标签的渲染链路，机器类型仍需实际样本 |
| 打开后继续浏览、展开的回答信息流 | 本次界面活动为 `feature.short_container_feature.ui.ShortContainerHostActivity`；APK 内存在原生 `ShortContent` 数据链路 | 不能把前两行或旧 `AnswerPagerFragment` 的覆盖范围直接套用到该场景 |

本次设备观察由另一调查分工记录：第三场景包含多个内联回答和 `sc_expand_layout_v2` / `sc_tv_expand_v2` 展开节点，展开后仍在同一 Activity，所取 UI 层级没有 `android.webkit.WebView` 节点。这支持继续检查原生数据链路；仅凭 UI 层级缺少 WebView 不能证明应用完全没有网页组件。

调查开始时模块的 `NextAnswer`、`Horizontal` 等 hook 主要修改切换按钮或手势，并未实现盐选内容分类。旧 `AnswerAppView` 的 HTML 加载研究不能替代第三场景的实际响应证据。

### “付费阅读内容仅盐选会员可评论”的具体字段链路

APK 字符串资源没有上述完整文案，但包含“仅允许会员/购买用户评论”以及作者、品牌关闭评论等不同提示。完整文案因此不能归因于一个固定的本地字符串资源。

确认了更直接的动态提示链路：

```java
// com.zhihu.android.comment.delegate.e
void a(com.zhihu.android.comment.a.a config) {
    // 正常实现的相关分支
    if (!config.canReply) {
        editTextView.setHint(config.placeHolder);
        editTextView.setCursorVisible(false);
    }
}
```

`comment.a.a` 是评论配置模型，含 `canReply`、`canSegmentReply`、`placeHolder` 等字段。`comment.delegate.s` 为 `CommentEditorView` 初始化此共享 delegate，初始化时取得评论配置，随后设置输入框提示。因此，观察到的特定付费文案有明确的候选元数据字段；下一步应把同一回答实际取得的配置与界面对应，不能仅从字段名称认定已匹配当前对象。

Retrofit 接口 `comment_for_v7.a.b` 确认正常配置请求：

```text
GET /comment_v5/{object_type}/{object_id}/config
```

`comment.g.l.a(String)` 对普通对象类型追加 `s`，所以回答的路径类型是 `answers`。另有段落评论的 `/segment/config` 路径，不能把两类请求混用。

评论列表响应 `comment_for_v7.b.k` 另含：

- `commentStatus`：模型 `b.l`，字段 `type`、`text`、`tipText`。
- `currentPermission`：模型 `b.o`，字段 `permission`、`text`、`disable`、`disableAlert` 等。
- `editStatus`：模型 `b.s`，字段 `canReply`、`toast`。

客户端评论权限设置有明确的 `paid` 模式，同时有 `nobody`、`followee`、`follower` 等模式。`paid` 是可验证的候选机器值，但本次静态分析尚未取得当前阳性回答的该值，也不能保证全部盐选内容都采用它。

必须排除以下误判：

- `canReply == false`、评论 `status == false` 只表示当前不可评论，普通回答也可能由作者或品牌关闭评论。
- 评论配置的 `permission` 子对象管理 `emoji`、`picture`、`voice`；其 `isVipPrivilege` 是这些评论功能的权益，不能据此把回答分类为盐选。
- `Answer.labelInfo.text` 是列表展示标签；评论许可和内容归属是不同维度。盐选标签可作为独立正向证据，但不能由标签缺失或普通评论关闭反推出非盐选或盐选。
- 完整的“付费阅读内容仅盐选会员可评论”作为专用提示，误删风险低于正文关键字“盐选”，但文案可能变化，也可能只有评论配置请求返回后才可获得。若要提前过滤列表，仍需验证该信息是否也存在于列表模型。

### 第三场景的原生数据、请求和列表边界

主要模型为：

```text
com.zhihu.android.service.short_container_service.dataflow.model.ShortContent
```

该模型有 `contentId`、`contentType`、`businessType`、`subType`、`commentStrategy`、`contentJsonNode`、`thirdBusinessJson` 和 `wrapper` 等字段。`CommentStrategy` 提供 `getCanReply()`、`getPlaceHolder()`、`getPlaceHolderStrategy()`；布尔 getter 确实是 `getCanReply()`。

`ShortContentWrapper` 管理 `isExpanded`、`contentNodeList`、`dataIndex`、`expandedContentNodePaging` / `expandedContentNodeTmpPaging` 等。内容展开的节点分页与整条回答信息流的分页是两层状态；过滤回答时不能只隐藏其中某个正文节点。

字段上的 Jackson 注解确认了实际 JSON 名称，避免根据 Java 属性名猜测：

| Java 模型字段 | JSON 字段 |
| --- | --- |
| `ShortContent.contentId` / `contentType` | `id` / `type` |
| `ShortContent.businessType` / `subType` | `business_type` / `sub_type` |
| `ShortContent.commentStrategy` | `comment_config` |
| `ShortContent.contentJsonNode` | `structured_content` |
| `ShortContent.thirdBusinessJson` | `third_business` |
| `CommentStrategy.canReply` / `placeHolder` | `can_reply` / `place_holder` |
| 独立评论配置 `comment.a.a.canReply` / `placeHolder` | `can_reply` / `place_holder` |

因此原生回答响应的 `comment_config.place_holder` 是值得优先核对的候选提前分类字段。静态模型确认它能承载提示，但仍需实际阳性对象证明当前页面也下发了该精确付费提示。

静态确认的解析入口：

```text
service.short_container_service.dataflow.repo.c.c.a(JsonNode) -> Object
```

正常实现调用 `ObjectMapper.convertValue(jsonNode, ShortContent.class)`，再补充 wrapper、第三方业务数据和内容分页等信息。它是观察真实模型的窄入口，尚未证明当前页面每条回答都通过该分支。

集合解析器：

```text
service.short_container_service.dataflow.repo.c.d.a(JsonNode) -> ZHObjectList
```

它分别解析根 `paging` 和 `data[]`，对每个 `data[]` 元素调用单项解析器，并把结果写入 `ZHObjectList.data`。另有 `repo.c.e.a(JsonNode)` 集合变体。随后 `repo.b.a` 等处理器把 `ShortContent` 转为多个 UI 节点，wrapper 还关联索引、展开和底栏状态。若后续实现过滤，应优先验证在整条内容转换为 UI 节点之前处理集合的方案；在展平后的节点列表逐项删除容易产生残余底栏、索引或展开状态失配。

Retrofit 接口 `service.short_container_service.dataflow.repo.a.c` 明确包含：

```text
GET /answers/v2/{id}
GET /next-render
GET /simple-next-render
GET /related_recommend/feed
```

`repo.d.a` 按 `contentType == "answer"` 选择 `/answers/v2/{id}`。另有明确标为降级的 `https://web-render.zhihu.com/content_detail?content_id=...&content_type=...` 构造路径；它不是已确认的当前主请求。具体查询参数和当前生效分支仍需实际请求证据。

### 可实施性与尚缺验证

第三场景已有具体的原生模型、单项和集合解析入口，可以独立设计过滤范围；目前没有足够证据把 `businessType`、`subType` 或 `thirdBusinessJson` 的某个未观察值写死为盐选。后续观察应采集阳性和普通回答的同类字段、专用评论提示、ID 关联及分页状态，避免保存或输出受限正文。

候选过滤需要保留请求完成、集合分页和展开正文分页；还需验证首次进入、连续翻页、整页过滤为空、展开/收起、缓存及混排内容。直接打开的目标回答是否也应跳过，需要单独确定用户预期，不能在全局反序列化阶段默认删除所有 `Answer` 或 `ShortContent`。

以上均为调查结果和候选方案，尚未进行 hook 注入后的命中确认、产品源码修改或过滤 E2E 验证。

## 本次手机实测、正常请求重放与捕获边界

### 当前阳性对象与入口

开始操作前手机已经解锁、亮屏。先取得当前截图和原生 UI 层级，未先切换页面。当前处于评论抽屉，底部输入提示确实是“付费阅读内容仅盐选会员可评论”，对应文本节点资源 ID `content`。本次未点击该输入区或任何会员 / 广告 / 购买入口。

关闭评论抽屉后，可见同一信息流中的多个内联回答；展开该卡片后仍停留在 `ShortContainerHostActivity`。当前卡片的可见作者与展开后新增的本地历史记录一致，关联回答 ID 为 `1977823141666501380`。该历史记录的 `content` 仍为空、`content_text_length` 为 0，`paid_info` 与 `label_info` 为空；这是有损历史保存，不能用来推断实际正文或当前 `ShortContent` 分类字段的值。

展开后的原生 UI 层级未出现 `android.webkit.WebView` 节点。结合实际 Activity 和展开控件，这足以纠正本次调查入口，应优先验证原生 short-container 链路；它不排除应用其他部分使用网页组件。

### 原生缓存与普通 HTTP 请求

本次限定查看了应用 `files/NetCache`，约 1.2 MB。可解析条目主要是推荐、关注、热榜、配置等对象，没有匹配当前回答 ID 的 `ShortContent` 数据。旧 HTML 缓存目录仍只有 35 字节 journal。缓存未命中只说明未从这些位置取得当前数据。

根据 APK 内 Retrofit 注解，使用应用已有 WebView cookie 做了两个普通、重建的 GET 请求：

| 路径 | 结果 |
| --- | --- |
| `/answers/v2/1977823141666501380` | HTTP 401，JSON，98 字节 |
| `/comment_v5/answers/1977823141666501380/config` | HTTP 401，JSON，98 字节 |

二者错误均为 `code=100`、`name=AuthenticationInvalidRequest`、`message=ERR_LOGIN_TICKET_EXPIRED`。这些是带已有 cookie 的重建请求；未捕获原生请求的 `Authorization`、完整查询参数或精确请求头，不能认作成功重放当前原生会话。没有刷新令牌、重新登录或改变账号状态。

另曾对前一次调查的 appview 路径做普通探测：未取得 HTML，直接请求在带 cookie 后仍为 HTTP 401。该路径属于旧 HTML 调查线，不是当前第三场景的响应证据。

### 最小动态观察的结果

本次没有直接重复上次的大脚本。临时启动匹配的官方 Frida `17.26.0` Android arm64 server，仅监听手机 `127.0.0.1`，通过本地 SSH 转发连接；先验证手机已解锁、亮屏，当前前台为 `ShortContainerHostActivity`，再附加当时主进程 PID `8578`。

分阶段的已收到事件如下，均不含正文或凭据：

```text
attached
native-ready (arch=arm64, pid=8578)
native-ping-confirmed
java-ready (available=true)
waiting-for-narrow-hook
detached (reason=process-terminated)
```

最小原生脚本和独立 `Java.available` 检查都已成功；随后进程终止，发生在窄 `ShortContent` / 评论配置 hook 安装之前。没有取得解析器事件、当前模型字段、正文长度或目标响应。没有进行后续附加、强制停止、应用启动 / 重启命令、安全策略调整或反调试绕过。

临时 server 日志和限定于旧 PID 的 crash buffer 都为空，尚不能解释进程终止原因，也不能据此认定存在反调试。系统后来自动出现新的知乎主进程；再次查看界面时已经回到首页推荐，原信息流未恢复。按本次限定范围，没有转去无关卡片补充对照，因此本次仍缺同一信息流中“普通且可评论”的正向对照。

### 清理和结论

临时 Frida 服务、手机二进制和日志、本地 SSH 转发以及包含账号信息、缓存、截图、数据库和响应副本的临时文件均已清理；仅保留本文中的脱敏元数据和失败边界。结束时再次验证 `device_locked=true`、`keyguard_showing=true`、`secure=true`、`screen_state=OFF`。

本次实测支持当前回答存在明确的盐选付费评论提示，并确认它位于第三种原生回答信息流。静态链路提供了 `comment_config.place_holder` 等具体候选分类字段，但本次没有取得这些字段在当前响应中的值，也没有普通回答的同类字段对照，暂不足以声称分类器已验证可用。

全文下发问题仍未解决：本次没有获得当前原生正文数据或分页响应，无法判定完整正文已经收到后隐藏，还是服务器仅返回当前可读部分。该未知不能由评论限制、历史字段为空、缓存缺失或动态观察失败代替。

## 原生账号凭据与请求签名续查

上一轮 WebView cookie 请求返回 401，不能证明应用正在使用的原生令牌已失效。本轮继续限定读取当前账号的应用内存储，没有登录、刷新令牌、改变权益或再次使用 Frida；手机全程保持安全锁定。

### 找到并对应了现有原生凭据

APK 中 `app.accounts.Account.getAccessToken()` 返回 `mToken.accessToken`。新存储基类 `app.accounts.a.a` 将每个账号的数据保存在：

```text
files/account/{activeNumericAccountId}/parcel/token
files/account/{activeNumericAccountId}/json/token
```

`app.accounts.a.d.i()` 选择 `parcel`，`app.accounts.a.c.i()` 选择 `json`；工厂按 parcel 优先、JSON 回退读取。JSON 通过应用 API mapper 序列化 `Token`，字段为 `access_token`。`app.f.a(Token)` 等正常请求辅助代码把它构造成 `Authorization: Bearer ...`。

本次由 `shared_prefs/account.xml` 的当前账号 ID 解析到对应目录，仅读取该账号的 token 文件。JSON 中存在非空 `access_token`，并确认同一字符串以 UTF-16LE 形式出现在当前账号的主 parcel token 文件中。原始值没有输出到工具日志或本文；临时副本使用私有目录和 `0600` 权限，结束时删除。

这确认了此次使用的是当前账号的原生存储凭据，而不是上一轮 WebView cookie；它仍不等于已经通过服务器验证该凭据在完整原生请求中的有效性。

### 单次普通请求及停止原因

使用该现有原生 Bearer 值，对官方接口进行了单次普通 GET：

```text
GET https://api.zhihu.com/answers/v2/1977823141666501380
```

返回 HTTP 403，JSON，214 字节，错误码 `40362`，含义为请求异常、暂时限制本次访问。没有返回正文或 `ShortContent` 数据。该请求仅使用已确认的 Bearer 规则与诊断用客户端标识，未取得并复现应用当前的完整请求头、签名或查询参数，因此不能称为精确重放，也不能把这一次错误归因于令牌失效或某一个缺失头。

收到该限制后停止请求，没有尝试替换猜测的请求头、重试签名、切换网络或绕过限制。没有继续请求第二个已知受限回答或普通回答对照。

### 忠实重放仍缺少的条件

正常网络 family 的 `net.common.OkHttpFamilyProviderImpl` 会加入 `net.d.k` 签名拦截器（PAPA family 有单独分支）。已确认签名输入包括精确编码后的路径 / 查询、`x-app-version`、`Authorization`、`x-udid`，再经回调进入 `com.zhihu.android.o.a.a(byte[])` 和 `com.bangcle.c.a(...)`，产生 `X-Zse-93` / `X-Zse-96`。

这里的受保护 provider 边界是 Java 静态入口，未在该边界确认 native 声明、硬件证明要求或完整运行时依赖；上述阻碍是缺少真实请求上下文和经过验证的重现方法，不是密码学不可实现的结论。

本次没有取得当前真实请求的全部输入和签名，未复现签名，也不能断言 40362 仅由缺少签名造成。现阶段忠实重放受阻于缺少实际现有请求的观察证据；若继续，应另行评估并授权应用内窄范围诊断，而不是继续猜测或重试 HTTP 请求。

本轮没有改变全文下发与机器分类的证据结论：原生凭据来源已明确，但当前正文、`comment_config` 和分页数据仍未取得。私有 APK、反编译输出、令牌副本和响应文件均已清理，结束时再次验证手机安全锁定。

## 手机离线期间的快照补充检查

本轮仅分析已成功取得的私有快照，没有访问手机或发出网络请求。范围限定为 `zhihu.db` 及其 WAL、`files/NetCache` 和外部 `fusion/HtmlData`。SQLite 在另一个私有临时目录的数据库 / WAL 副本上读取，原快照保留供其他离线工作使用。没有记录正文、账号资料、认证数据或缓存 URL。

### 缓存实际覆盖范围

两个已知受限回答均能在历史中重新定位：`1977823141666501380` 对应问题 `1918993512029975976`，`2102668829868254943` 对应问题 `412372409`。它们的历史正文仍为空、`content_text_length=0`、`paid_info` / `label_info` 为空。此处只用历史确定可重现的对象，不用这些空字段判断是否付费或是否收到全文。

推荐缓存 `FeedList` 的记录时间为 2026-11-10 09:09:13（北京时间）。其中有 4 条非空回答卡；`extra.content_id` 给出不同的实际回答 ID，而它们的顶层 `ComponentCard.id` 相同。因此后续关联事件应检查真实内容 ID，不能只用顶层卡 ID。

`extra.business_ext_map.ori_content` 是 Base64 编码的 JSON。本次已实际解码检查：它承载内容 ID、类型、路由 / 排序等元数据，不是已取得的回答正文。四条卡均有 `content_type=answer`、`typedContentType=Answer`、`deliver_type=Normal`；这些值不能直接证明内容是普通免费回答。在这些卡及已解码元数据中，没有取得精确盐选付费提示、`comment_config` 或 `structured_content`。

另一个 `ZHFollowObjectList` 缓存的记录时间为 2026-10-29 08:46:57，明显早于本次页面。其 6 条非空回答卡都包含：

```text
extra.business_ext_map.content_info.sub_type = "normal"
extra.business_ext_map.sdui_business_type = ""
```

这组旧数据可作候选对照，但尚未与同一数据链路中的付费阳性值比较。部分卡保存了超过摘要长度的 `content_info.detail.plain_content`，例如一条为 1003 字符、摘要 213 字符；另一条为 21988 字符、摘要 200 字符。它们说明这个旧缓存能保存较长文本，不能证明当前受限回答的剩余正文也已收到。

限定的 NetCache 原始字节扫描（UTF-8 / UTF-16LE）没有命中两个已知受限 ID或精确提示“付费阅读内容仅盐选会员可评论”“选自盐选专栏”；已解析回答卡中也未取得相应付费字段值。这不是对全部编码形式的排除，不能据此推断缓存中绝无其他编码标记。所查 `HtmlData` 仍只有 35 字节 journal，没有可分析的目标 HTML。

### 回到手机后使用的最小对象清单

| 回答 ID | 问题 ID | 当前证据和用途 |
| --- | --- | --- |
| `1977823141666501380` | `1918993512029975976` | 第三场景的精确盐选付费评论提示阳性；优先关联真实模型 |
| `2102668829868254943` | `412372409` | 前次广告解锁门槛阳性；单独记录门槛类型，不能当作同一种盐选协议 |
| `2103035398217900188` | 未取得 | 本次新推荐缓存中的候选对照；尚未验证免费或可评论 |
| `2098545739794233094` | `2098386845084148664` | 10 月 29 日旧关注缓存，`sub_type=normal`，文本长度大于摘要；待重新验证 |
| `99433728962` | `24091593` | 同一旧缓存的长文本候选对照；待重新验证 |

后续应先确认候选对照的实际评论许可与当前权益，再按推荐卡、问题回答列表、打开后继续浏览 / 展开的第三场景分别关联同一回答的字段和分页。候选对照未通过这一步前，不能把“未找到付费提示”用作普通回答标签。

离线检查没有补上当前受限正文或付费 / 普通机器字段对照的缺口，也没有修改或部署任何 hook。原始私有快照继续保留，本轮生成的数据库工作副本已清理。

## 正常客户端实测结论与恢复

### 本次正文下发结论

通过同包、同签名的临时 Xposed 观察模块，在不改变请求、返回值或账号权益的情况下，取得回答 `1977823141666501380` 的正常客户端 `answer_detail` HTTP 200 数据。观察到的完整 HTTP body 窥视值为 **35588 字节**，未达到 2 MiB 上限，来自网络而非缓存；供私下检查的文件是该 JSON 解析后重新序列化的 **35337 字节快照**，不是逐字节原始响应。

实际解析数据为：

- `structured_content.segments` 共 158 项：头卡 `km-paid-answer-header`、156 个 paragraph、末尾卡 `km-paid-answer-tail-truncate`；完整聚合扫描未触及任何上限。
- 156 段识别故事文本合计 2787 字符。UI 最后可见的 28 字符片段在故事流中恰好命中一次，位于末尾，其后故事字符数为 0。未保存或输出该片段或正文。
- 最后一项是带“精彩内容待解锁 / 看广告免费解锁”的门槛卡，后面没有故事节点。实际界面也已滚动到该门槛，没有点击广告、会员或购买入口。
- 原生展开 / 临时展开分页均 `is_end=true`。已核对正常续页入口会在此状态直接返回，不因 `next` 字段仍有值就发请求。本次没有手工请求该 URL 或制造解锁状态。

为排除“完整故事藏在别的 JSON 字段”，对这一次快照检查了全部 33 个根字段和 135 组字符串字段路径，递归解析两张卡的 `extra_info` 与分页，共 3 处 JSON 字符串，无解析失败。头卡实际字段是标题行、会员提示、版权、价格业务 ID 等；尾卡实际字段是按钮、价格、优惠、广告配置和跳转等，没有另一份故事字段。`structured_content_vip=null`、`video=null`、`thumbnail` 为空、`catalog` 为空；摘要规范化后为故事开头的 193 字符。还保留了少量非 UTF-8 标识 / 跟踪字段的路径和长度清单，没有将它们当作故事文本，也没有发现它们进入当前原生故事渲染链路。

尾卡实际含 `authentication_result=true`，这不与门槛矛盾：静态视图仅在“当前账号是 VIP 且该字段为 false”时切换重试控件；其余情况仍可渲染普通解锁卡。因此不能把这个布尔值单独解释成完整正文权益。

以上支持：**这一次正常原生接收的可渲染故事只有预览，并非完整故事已收到后仅靠 UI 隐藏。** 它不证明服务器对其他请求永远不下发全文，也不覆盖尚未采到的网页阅读、其他账号或解锁后的路径。

### 付费与普通字段的实际对照

| 入口 / 对象 | 回答 ID | 实测字段与界面关联 |
| --- | --- | --- |
| 问题回答列表付费样本 | `1975240497678213437` | `business_type=paid`、`paid_info` 存在、精确标签“选自盐选专栏”；标签实际可见，点击后页面与显式目标的首段片段一致 |
| 同一问题列表普通样本 | `2035050147814577968` | `business_type=normal`、`paid_info` 不存在；点击后普通页面正常显示，原生详情 `can_reply=true` |
| 原生付费详情 | `1977823141666501380` | `business_type=paid`、`can_reply=false`、精确付费评论提示；UI 可见广告解锁门槛 |
| 原生普通长文对照 | `99433728962` | `business_type=normal`、`can_reply=true`；评论 UI“理性发言，友善互动”；146 / 146 segments 完整聚合，145 段故事共 21713 字符，没有 KM 付费门槛 |
| 同一付费对象的相关回答折叠态 | `1975240497678213437` | `business_type=paid`，但评论提示为空；存在普通 `CutoutNode`、`cutout_requested=true` 和未结束分页；打开详情后提示和末页状态才补齐 |

这些样本为问题列表和原生回答数据链路提供了具体分类依据，尚未实现或 E2E 验证最终过滤功能。尤其不能只依赖评论提示：真实付费对象在折叠态也可能没有提示。也不能只看 `can_reply=false`，普通但作者关闭评论的负例本次未自然采到。

普通长文和同问题普通回答中都出现 `reward_tail_truncate`，而其业务类型为 normal、可评论且没有已知付费门槛。这一节点名称不能单独作盐选分类器。普通展开 / 收起的 `CutoutNode` 与末尾 KM 付费门槛同样是不同机制。

### 推荐入口仍有缺口

按限定只看了 4 屏自然推荐，没有点选内容来改变兴趣或刷新制造阳性。期间记录 21 条 Card 元数据（包含后台预取，不是 21 条逐一验证的可见样本），其 `business_type` / `sub_type` 均未提供可用值；未见可确认的付费阳性。这不能证明推荐中没有付费内容，也不能证明观察器已完整覆盖推荐。

本轮部署的 legacy 探针覆盖 Answer 和部分 SDUI Card，但未完整覆盖 `ApiTemplateRoot`、`PaidContent`、`MomentsPaidColumn` / `MomentsManuscript` 等推荐模型，以及可能已被既有过滤修改或移除的对象。现场原有开关保持不变：`switch_mainswitch=true`、`switch_marketcard=true`、`switch_feedad=true`、`switch_removearticle=true`；`switch_pin=false`、`switch_video=false`。这些条件可能影响推荐样本，后续应先补观察覆盖，再验证真正阳性，不能扩大未经验证的规则。

### 证据、隐私与恢复

已保存不含正文、认证值或 URL 的本地证据：

- [完整字段 / 嵌套 JSON 检查结果](../evidence/2026-11-10-premium-observer/selected-response-evidence.json)及[分析脚本](../evidence/2026-11-10-premium-observer/analyze_selected_response.py)。私有输入已删除；脚本保留检查方法，不包含输入内容。
- [入口样本与恢复检查](../evidence/2026-11-10-premium-observer/sample-comparison.json)。
- [推荐 Card 逐 ID 元数据](../evidence/2026-11-10-premium-observer/recommendation-discovery.json)。
- [与实际部署 capture APK 对应的冻结观察器源码](../evidence/2026-11-10-premium-observer/observer-source/PremiumObserver.java)及[注册改动](../evidence/2026-11-10-premium-observer/observer-source/Hooks-registration.patch)。后续推荐探针修改不冒充这一次的运行源码。

私有 JSON 快照、锚点、界面截图、数据库工作副本和含账号信息的离线应用数据 / 缓存均已删除。只保留后续静态工作需要的 APK、观察器构建产物、原机恢复 APK 及上述脱敏证据。

结束前已覆盖恢复原机 APK，并逐字节核对与 `backup/premium-observer-restore/BetterZhiliao_installed_restore.apk` 一致。新启动的知乎不再生成诊断日志；普通回答可见加载正常，问题列表的盐选标签实际渲染正常。48 个模块设置全部一致，本模块启用状态和整个作用域集合未变。手机上的诊断文件与暂存 APK 均逐项确认不存在，最终安全锁定、锁屏显示、屏幕关闭。没有重启手机、杀全局 LSPosed daemon、改变权益或保留诊断模块。

## 推荐前过滤元数据补充复核

为修正旧探针对 `Map<String,JsonNode>` 的读取及模型覆盖不足，另行部署了仅观察推荐元数据的临时模块，不注册网络 / 正文采集。它给同一反序列化调用分别安装 LOWEST / HIGHEST 优先级的 after 回调，并记录顺序和结果是否被中间回调移除，未改变设置或返回值。

在 MainActivity 推荐页四屏自然观察期间：

- 取得 22 条 SDUI Card 元数据：14 个 `answer`、4 个 `article`、4 个 `pin`；包含后台预取，不能全部认作逐项可见样本。
- 22 对收据均实际观察到 LOWEST 回调先执行。4 个 `article` 在随后的回调间被移除，与原有 `switch_removearticle=true` 相符；它们不是付费阳性，不能拿来证明盐选过滤。
- 没有采到精确付费标签、可用的付费业务值或其他受支持付费模型族。Map 节点现在确实被读取，但嵌套作者等元数据触及深度限制，已明确记录；不能把未知字段当作已排除。
- 观察窗口属于全局反序列化，未记录请求端点或根集合关系。当前前台是推荐页，不代表所有后台回调都对应屏幕上的推荐卡。尚未采到的 PaidContent / Manuscript / MarketCard 等不能补作成功。

结果只补上了回调顺序和 Map 读取的证据，**仍没有有效的推荐付费阳性对照**。后续若用已知付费对象做定向正常搜索，也须记录实际返回的模型类与 ID，不能把 legacy Answer 搜索结果冒充首页 SDUI Card 验证。

本次元数据和源码保留为 [前过滤探针结果](../evidence/2026-11-10-premium-observer/recommendation-prefilter-summary.json)、[配对事件](../evidence/2026-11-10-premium-observer/recommendation-prefilter-events.jsonl)和[冻结源码](../evidence/2026-11-10-premium-observer/recommendation-observer-source/RecommendationObserver.java)。模块已按相同原机 APK 还原，48 个原设置及整个启用 / 作用域集合核对一致；另按后续 v0.2 安装任务要求，在私有备份中保留最小设置和作用域导出，未卸载原模块。
