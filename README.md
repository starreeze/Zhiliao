# BetterZhiliao

基于 [Shatyuka/知了](https://github.com/shatyuka/Zhiliao) 的知乎去广告 Xposed 模块。本分支维护版本为 **0.2**。

## 0.2 更新

- 应用包名改为 `com.starreeze.betterzhiliao`，显示名称保持 BetterZhiliao。
- 新增三个独立的盐选、会员付费回答过滤开关，分别作用于推荐信息流、问题下回答列表、打开回答后的连续浏览流。回答浏览流过滤保留主动打开的回答，仅过滤后续浏览流中的盐选、会员付费回答；三个开关默认均关闭，启用后重启知乎生效。
- 推荐流仅过滤有明确付费标记的回答；部分推荐卡片可能无法识别，保留显示，不额外请求回答详情。
- 新增“去悬浮广告”和“去启动弹窗广告”开关，默认开启并受总开关控制。前者作用于首页图片广告和视频结束后的广告挂件；后者阻止首页透明视频广告弹窗。两项不更改原启动页广告开关，也不拦截通用弹窗或导航。
- 移除 Star、Wiki、Telegram 和捐赠推广入口，保留原作者、原项目链接与 GPL 许可证。

## 0.1 修复

- 修复新版推荐卡片中的 HTML 文本模型导致自定义过滤中断的问题；提前应用视频、文章、想法等类型过滤。
- 修复“禁止自动刷新”误拦截推荐流共享加载流程导致持续转圈的问题，保留明确的定时刷新和切换标签刷新拦截。
- 应用及设置页名称改为 BetterZhiliao，包名仍为 `com.shatyuka.zhiliao`。

已在知乎 **11.5.0（40530）** 上进行限定范围验证。测试覆盖、加载状态观察和兼容性边界见 [0.1 发布与验证记录](docs/CHANGELOG.md#betterzhiliao-01)。

## 功能

- 广告
  - 去启动页广告
  - 去首页悬浮图片广告和视频结束后的广告挂件
  - 去首页启动透明视频弹窗
  - 去信息流广告
  - 去回答列表广告
  - 去评论广告
  - 去分享广告
  - 去回答底部广告
  - 去搜索广告
- 其他
  - 过滤视频
  - 过滤文章
  - 去信息流会员推荐
  - 去回答圈子
  - 去商品推荐
  - 去相关搜索
  - 去关键字搜索
  - 直接打开外部链接
  - 禁止切换色彩模式
  - 显示卡片类别
  - 状态栏沉浸
  - 禁止进入全屏模式
  - 解锁第三方登录
- 界面净化
  - 移除直播按钮
  - 不显示小红点
  - 隐藏会员卡片
  - 隐藏热点通知
  - 精简文章页面
  - 隐藏置顶热门
  - 隐藏混合卡片
- 导航栏
  - 隐藏会员按钮
  - 隐藏视频按钮
  - 隐藏关注按钮
  - 隐藏发布按钮
  - 隐藏发现按钮
  - 禁用活动主题
  - 隐藏导航栏突起
- 左右划
  - 左右划切换回答
  - 移除下一个回答按钮
- 自定义过滤
- 注入JS脚本
- 清理临时文件

## 帮助

- [0.2 发布与验证记录](docs/CHANGELOG.md#betterzhiliao-02)
- [盐选内容三场景调查与内容完整性记录](docs/history/2026-11-10-premium-three-scenarios.md)
- [0.1 发布与验证记录](docs/CHANGELOG.md#betterzhiliao-01)

## 下载

- [BetterZhiliao Github Releases](https://github.com/starreeze/Zhiliao/releases)：本分支发布入口，正式包名为 `BetterZhiliao_0.2.apk`。
- [Github Actions](https://github.com/starreeze/Zhiliao/actions)：CI 构建产物；快照版本附带提交短编号。

0.2 使用独立包名，移除旧模块并安装新模块后，需在 LSPosed 中启用新模块并勾选知乎作用域，避免两个模块重复加载。设置仍保存在知乎应用的 `zhiliao_preferences` 中，原有设置键不变；只卸载模块，不要卸载知乎或清除知乎数据。模块替换后须重启知乎。

本地构建未配置发布签名时使用本机 Android 调试签名；后续同包名更新仍要求签名一致。CI 未配置共享签名时不保证与本地构建签名相同。

## 上游与致谢

原项目由 **Shatyuka** 创建，本分支保留原项目版权与 GPL 许可证。[原项目源代码](https://github.com/shatyuka/Zhiliao)，[BetterZhiliao 分支源代码](https://github.com/starreeze/Zhiliao)。

本分支于 2026-11-10 修改包名、设置界面和付费回答过滤功能。软件不提供任何担保，可按 GPL-3.0 的条款使用、修改和再分发。

## License

This project is licensed under the [GNU General Public License, version 3](LICENSE.md). BetterZhiliao is a fork of [Shatyuka/Zhiliao](https://github.com/shatyuka/Zhiliao); upstream attribution and license are preserved.
