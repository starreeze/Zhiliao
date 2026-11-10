# 盐选内容过滤可行性调查

调查对象为手机上已安装的知乎 11.5.0。结论：可以设计推荐流、问题回答列表两个独立过滤开关；现有模块尚未实现这种完整的盐选过滤。回答列表已有明确的展示字段和列表处理入口；推荐流仍需取得当前实际下发的盐选卡片样本，确认具体模型和字段。

本次仅进行界面观察、模块源码阅读和已安装 APK 的静态分析。未修改产品源码、构建或安装 APK，也未切换过滤设置或验证过滤后的运行效果。

## 界面观察

- 推荐页已抽样初始 3 屏及后续 8 屏，尚未取得明确标记的盐选内容。这个结果不能证明该页没有盐选，也不能证明现有功能已过滤它。
- 问题 `35334758` 的回答列表可见普通回答与两条带蓝色“选自盐选专栏”标记的回答混排。标记的 UI 资源 ID 为 `com.zhihu.android:id/label_text`。
- 操作结束后已确认安全锁屏：`device_locked=true`、`keyguard_showing=true`、`secure=true`。临时截图、界面 XML、APK 和反汇编中间文件已删除。

## 现有模块的覆盖范围

`app/src/main/res/xml/preferences_zhihu.xml` 的 `switch_marketcard`（“去信息流会员推荐”，默认关闭）对应 `CustomFilter` 对整个 `com.zhihu.android.api.model.MarketCard` 对象的移除。这只能说明它针对会员推荐卡，不能推出它覆盖所有盐选回答或盐选专栏。

`CustomFilter.java` 当前通过 `ZHObjectRegistryCenter$InnerDeserializer.deserialize` 的返回值识别以下模型：

- `ApiTemplateRoot`：读取 `extra.type` 和 `common_card.feed_content`。
- SDUI `Card`：读取 `extra.contentType`，以及标题、作者、摘要的 `TextElement` / `HtmlTextElement`。
- `MarketCard`：按会员推荐开关移除。

过滤条件包括视频、文章、想法、`SvipActivity` 和标题/作者/摘要正则；没有盐选的专门判定。现有文本提取不读取下面确认的 `Answer.labelInfo`，也不遍历全部 SDUI 标签。把“盐选”放进普通正文正则会误删讨论盐选的普通内容，并漏掉仅在标签中标记的卡片。

`AnswerListAd.java` 只让 `MorphAdHelper.resolveAnswerAdParam(...)` 返回 `false`，并跳过 `AnswerListWrapper.insertAdBrandToList(...)`。它处理广告插入，未过滤具有盐选标签的回答。

## APK 中确认的字段和处理入口

以下为直接读取 DEX 字段和指令后整理的逻辑摘要，不是未经验证的接口猜测。

### 回答列表的盐选标签

`com.zhihu.android.question.list.holder.view.AnswerBottomMetricView.setLabelSpecial(Answer)` 调用：

```java
CommonLabelInfo label = CommonLabelInfo.transFormer(answer);
// 存在标签及前景颜色时，设置标签背景并显示 ItemLabelView。
itemLabelView.setLabelInfo(label);
itemLabelView.a();
```

`CommonLabelInfo.transFormer(Answer)` 明确把 `Answer.labelInfo.text` 复制到 `CommonLabelInfo.content`，同时复制 `labelInfo.type`、`icon_url`、`foregroundColor` 等。`ItemLabelView.a()` 再把 `CommonLabelInfo.getContent()` 传给内部 `TextView.setText(...)`。因此针对 `Answer.labelInfo.text` 中的精确“选自盐选专栏”标记进行过滤，具有比正文关键字更直接的依据。APK 字符串资源中没有这个完整文案，说明它并非固定的本地字符串资源。

该链路确认了字段的展示意义，但本次未读取上述两条在线回答的 Java 对象或响应体，尚未直接确认其 `labelInfo.type` 的机器值。不能把所有非空 `labelInfo` 都当作盐选；其他内容也使用同一标签模型。精确标签文案可以作为首个限定规则，后续取得真实样本后再补充类型或链接规则。

`Answer` 另有 `answerPaidInfo`、`answerType`、`businessType` 字段。`AnswerPaidInfo` 只有 `hasPurchased`、`isOfficial` 等属性；静态代码也把它用于视频缩略图路由分支。本次证据不足以将 `answerPaidInfo != null`、作者会员身份或 `businessType != "normal"` 等同于盐选，不能直接用作唯一条件。

### 回答列表的首次加载和翻页

确认入口：

```java
AnswerListWrapper.setupAnswerData(IAnswerList answerList, boolean isRefresh)
```

其逻辑设置 `isRefresh`、`hasAnswerRequestFinish`，调用 `addAnswerListToData(...)`，再备份回答。后者区分首次/刷新与加载更多，读取 `IAnswerListKt.data(answerList)`，执行去重并填充展示列表；分页对象单独从 `IAnswerListKt.paging(answerList)` 复制到 wrapper。

列表元素可能是直接的 `Answer`，也可能是 `MixShortCardTargetWrapper`。`getBigCardOrSmallCardData(Object)` 明确处理 wrapper 中的 `Answer`，并按卡片模式选择保留 wrapper 或转换为 `Answer`。实现需同时识别这两种形式，保留其他类型的列表项。

这个入口适合在限定为问题回答列表的上下文中移除匹配项，同时保留正常请求完成流程与原分页数据。它是静态确认的候选 hook，尚未经注入验证。

### 推荐流的模型证据

`FeedZHObjectDeserializer` 明确注册以下 JSON 类型：

```text
paid_content            -> com.zhihu.android.api.model.PaidContent
paid_column_moment      -> ...extension.MomentsPaidColumn
paid_column_manuscript  -> ...extension.MomentsManuscript
```

旧式 feed 渲染代码也有 `PaidContent` 分支，可读取其 `labelInfo.text` 作为标签；客户端的会员业务类型映射还明确将 `paid_column` 对应到“盐选专栏”。这证明客户端包含可结构化识别的盐选相关内容类型，但不能证明当前推荐页仍以这些形式下发。`MomentsManuscript` 还同时承载 `ebook_manuscript`、`album_manuscript`、`instabook_manuscript` 等类型，不能按这个 Java 类整体删除。

当前 SDUI `Card.Extra` 包含 `contentType`、`businessExtJson`、`customInfo`、`promotion` 等字段；本次未取得推荐页盐选阳性卡片，不能确定哪一个字段及取值可完整区分它。建议以真实阳性卡片为基准核对结构化类型、专用标签或目标链接，再加入规则，不能只凭字段名称推测。

## 建议的实现与验证边界

增加两个独立开关：“过滤推荐流盐选内容”和“过滤问题回答列表盐选回答”。过滤范围应分别限定在推荐列表和问题回答列表的数据处理处；现有全局反序列化 hook 没有页面范围参数，直接在其中按 `Answer` 全局删除可能影响其他页面。

回答列表可先使用精确标签规则，兼容直接对象与混合卡片 wrapper；推荐流需补充阳性样本后确定覆盖范围。会员推荐卡广告、作者的会员身份与内容属于盐选是不同条件，不能互相替代。

只删除列表项，不取消请求、不吞掉成功回调、不修改 `paging.next` / `isEnd` 或伪造刷新来源。项目此前修复的 `AutoRefresh.java` 已明确要求保留首次加载、手动刷新及预加载消费者依赖的完成回调。

需要实际验证首次加载、刷新、连续加载更多、普通/盐选混排，以及整页全部被过滤的情况。即使保留了分页对象，原列表框架仍可能把空数据页解释为结束；必须确认它能继续访问服务器给出的下一页，必要时加入有界续取，并防止无进展循环。还需确认数量提示、去重、备份与相邻回答导航不会因删除列表项而失配。

目前可确认“存在可实施的入口与精确标签依据”，尚不能声明“两个页面已支持完整盐选过滤”或“分页和误删已通过 E2E 验证”。
