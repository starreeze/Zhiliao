# 当前受限回答的正文下发调查

目前无法判定当前回答是“完整正文已经下发、客户端隐藏后半部分”，还是“服务器只下发可读部分”。已确认实际解锁提示和客户端 HTML 加载边界，但未取得当前页面的原始 HTML / 正文响应，不能用界面或缓存缺失替代响应证据。

本次对象是手机上知乎 11.5.0 中当前展示的回答，回答 ID 为 `2102668829868254943`。调查包括受限内容界面的正常滚动、缓存和数据库的限定只读检查、已安装 APK 的静态分析，以及随后一次临时动态观察尝试；没有点击广告解锁或购买入口、改变权益、修改模块源码、安装 APK 或绕过访问限制。临时动态观察未成功取得页面响应，具体失败见下文。结束时已确认手机安全锁定；临时截图、数据库副本、APK、静态分析中间文件及临时动态观察工具均已清理。

## 当前页面的直接观察

页面正文可正常滚动多个章节，最终出现“精彩内容待解锁”，主按钮为“看广告免费解锁”，另有“¥30开通超级会员阅读免广告”。这说明当前页面使用广告解锁 / 会员免广告门槛，不能仅因为它是受限回答就认定它与全部盐选专栏使用同一种正文下发机制。

当前问题标题与本地历史中的最新回答记录对应。该历史记录只有约 210 字的 `excerpt`，而 `content`、`paid_info`、`label_info`、`business_type` 均为空，`content_text_length` 为 0。由于界面实际已显示比该摘要长得多的正文，这条记录明显是历史用途的有损保存，不能据此判断网络响应只包含摘要。

## APK 静态确认的加载链路

`com.zhihu.android.answer.module.content.appview.AnswerAppView.setContent(long, long, boolean)` 设置回答 ID 和缓存选项，然后构造 URL 并调用 `loadUrl(...)`。

旧分支 URL 构造器 `com.zhihu.android.content.utils.a` 明确包含：

```text
https://www.zhihu.com/appview/v2/answer/{answerId}?config=...
```

另有由功能开关选择的 `AnswerAppViewQuery.getAppViewQueryUrl(...)` 分支；上述模板不能代替对当前请求完整 URL、参数和响应的实际确认。

`AnswerAppView` 的父类为 `com.zhihu.android.appview.a`。其 `fetchHtmlData(...)` 包含从缓存 / 预加载读取 HTML 的分支，以及调用网络层、读取 `okhttp3.ResponseBody.string()` 的分支。正常网络分支随后把 HTML 交给 `processHtmlContent(...)`。

`AnswerAppView.processHtmlContent(String, renderMode)` 记录加载状态后调用父类同名方法；父类的正常实现直接返回传入的 HTML 字符串。在已检查的这一个原生加载边界，没有发现按会员或购买状态截取正文的逻辑。这只说明该边界不截取，不能证明服务器 HTML 包含完整正文，也不能排除网页 JavaScript 按数据和权益控制显示或后续请求。APK 的热修复分支还可能替换正常实现。

`AnswerPlugin.dispatchPaySuccessToHybrid(...)` 可向网页发送 `purchaseStatusChange` 事件，数据包含 `sku_id`、`is_member` 等。这个事件证明客户端存在权益状态通知，不证明完整正文预先存在于页面，更不应把发送事件当作获取权益的方法。

本次限定静态检查未将页面的广告解锁提示对应到确定的原生正文截断分支。`Answer` 的 `adAnswer` 模型只有 ID、插入标志、类型和广告跟踪等字段；不能凭 `ad_answer` 是否为空判断正文解锁状态。原生代码中的部分 `reward` 方法处理作者赞赏，也不能据名称认作“看广告解锁”。当前门槛的具体网页逻辑仍需实际页面资源或响应佐证。

## 缓存检查及其含义

静态代码确认回答 HTML 缓存具有以下结构：

```text
Application.getExternalCacheDir()/fusion/HtmlData
logical key: cache://html/answer/{answerId}
model: type, id, content, config, scroll, time
```

`com.zhihu.android.appview.a.b` 负责该目录与逻辑键，基类 `community.cache.c` 使用 Jackson 序列化 JSON，更底层使用 OkHttp `DiskLruCache`。

手机上的对应目录存在，但只有一个 35 字节的 journal，没有目标回答的 HTML 数据文件。`manuscript_preload_html.db` 中没有匹配当前开头的内容；限定检查的 WebView HTTP 缓存也未找到匹配。未发现可用的 WebView 调试 socket。

这些结果说明本次未在已检查位置取得页面响应。它们不能证明响应未进入进程、正文只在内存中、缓存功能被关闭，或服务器已经截断正文；不同路径、清理策略和内存预加载均可能影响落盘。

## 与原生专栏阅读器的区别

APK 中另有 `reader_common.api.model.NetManuscriptData` / `vip.manuscript.api.model.NetManuscriptData`，包括 `authenticationResult`、`isFree`、`isLock`、`truncateRatio`、`truncateSessionId` 等字段；还有独立的专栏预加载数据库。

这些字段说明原生阅读器协议包含权益和截断相关信息，但没有确认当前广告解锁回答采用该模型。不能把字段名称当作“当前回答全文已经收到”或“当前回答由客户端截断”的证据。

捆绑资源 `assets/manuscript_tars_default_html` 只是将 `#manuscript_content#` 插入 `manuscript_content` 容器；在该模板本身没有发现专用于存放隐藏完整正文的第二个节点。这也不能代替当前回答网页的实际 HTML / DOM 检查。

## 实际响应捕获尝试及失败边界

用户进一步允许重新加载 / 重放同一账号的正常请求，以检查本次实际收到的数据。现有调查材料未提供可直接重放的目标完整请求；尝试读取所查 WebView 目录的 `History` 文件时发现该文件不存在，未取得目标请求 URL。手机上也没有运行中的 Frida 服务或监听端口。

因此临时使用与本机 Python Frida 一致的官方 `17.26.0` Android arm64 server，只监听手机 `127.0.0.1`，通过本地 SSH 转发连接。此工具只用于附加现有进程并观察 `ResponseBody.string()`、`AnswerAppView.processHtmlContent(...)` 和 WebView 的加载边界；没有设置返回值替换、权益修改或解锁事件。计划先确认观察器就绪，再进行正常重载。

第一次附加发生在手机仍安全锁定、应用进程处于冻结状态时，失败为：

```text
frida.TimedOutError: unexpectedly timed out while waiting for stop from process with PID 2846
```

随后手机成功解锁，原 PID 已消失，知乎主进程变为 PID `13196`。界面仍能显示同一回答，位置回到正文开头。没有发送强制停止或重启应用命令；进程消失与此次附加时间接近，但没有取得崩溃证据，不能确认因果关系。

仅再尝试了一次前台现有进程附加。该次运行已到 `script.load()`，但失败为：

```text
frida.TransportError: timeout was reached
```

未收到观察脚本的就绪信号，没有取得目标 URL、HTML 或正文响应，也没有完成“观察器就绪后的受控重载”。结束前主进程仍存在。遇到第二次失败后停止了动态观察，没有追加进程重启、安全策略调整或反调试绕过。

已终止临时 Frida server 和本地转发，删除手机临时二进制与日志、本地临时脚本与截图 / 数据副本，并再次确认安全锁定。以上失败只能说明这次捕获方法没有取得证据，不能用于判断服务器是否下发了完整正文，也不能说明应用必然实施了反调试。

## 还缺少的决定性证据

需要在保持当前合法权益不变的情况下，取得这一次加载实际收到的主文档 HTML、相关正文 JSON 响应，或对应的进程内页面数据，并关联到同一回答、同一次加载。分析时只需保留结构、正文长度、预览 / 解锁状态和截断位置，不必输出受限全文。

如果原始响应或已收到的页面数据中能确认解锁点以后的正文，并能对应到隐藏 / 截取逻辑，才支持“已下发但隐藏”。如果当前正文响应只含解锁点前的内容，并由后续请求按权益返回其余部分，才支持该请求路径的“只下发预览”。仅检查一个响应字段仍需排除内容被拆分到其他响应或编码字段的情况。

本次结论限于：已确认广告解锁门槛；已检查的原生 HTML 传递边界不截取字符串；当前原始响应未取得，全文是否已下发仍未定。
