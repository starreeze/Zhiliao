# BetterZhiliao

基于 [Shatyuka/知了](https://github.com/shatyuka/Zhiliao) 的知乎去广告 Xposed 模块。本分支维护版本为 **0.1**。

[![Download](https://img.shields.io/github/v/release/starreeze/Zhiliao?label=Download)](https://github.com/starreeze/Zhiliao/releases/latest)
[![Stars](https://img.shields.io/github/stars/starreeze/Zhiliao?label=Stars)](https://github.com/starreeze/Zhiliao)
[![License](https://img.shields.io/github/license/starreeze/Zhiliao?label=License)](LICENSE.md)

## 0.1 修复

- 修复新版推荐卡片中的 HTML 文本模型导致自定义过滤中断的问题；提前应用视频、文章、想法等类型过滤。
- 修复“禁止自动刷新”误拦截推荐流共享加载流程导致持续转圈的问题，保留明确的定时刷新和切换标签刷新拦截。
- 应用及设置页名称改为 BetterZhiliao，包名仍为 `com.shatyuka.zhiliao`。

已在知乎 **11.5.0（40530）** 上进行限定范围验证。测试覆盖、加载状态观察和兼容性边界见 [0.1 发布与验证记录](docs/release-0.1.md)。

## 功能

- 广告
  - 去启动页广告
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

- [0.1 发布与验证记录](docs/release-0.1.md)
- [上游 Github Wiki](https://github.com/shatyuka/Zhiliao/wiki)

## 下载

- [BetterZhiliao Github Releases](https://github.com/starreeze/Zhiliao/releases)：本分支发布入口，正式包名为 `BetterZhiliao_0.1.apk`。
- [Github Actions](https://github.com/starreeze/Zhiliao/actions)：CI 构建产物；快照版本附带提交短编号。

保留上游包名是为了维持模块识别和配置兼容；安装仍要求签名一致。本地发布构建未配置正式签名时使用本机 Android 调试签名，与上游签名不同，不能直接覆盖上游包。具体安装边界见[发布记录](docs/release-0.1.md)。

## 上游与致谢

原项目由 **Shatyuka** 创建，本分支保留原项目版权与 GPL 许可证。上游资源：[源代码](https://github.com/shatyuka/Zhiliao)、[发布页](https://github.com/shatyuka/Zhiliao/releases)、[Xposed Repo](https://repo.xposed.info/module/com.shatyuka.zhiliao)、[Telegram 频道](https://t.me/zhiliao)、[Telegram 群](https://t.me/joinchat/OibCWxbdCMkJ2fG8J1DpQQ)。这些入口属于上游。

## License

This project is licensed under the [GNU General Public License, version 3](LICENSE.md). BetterZhiliao is a fork of [Shatyuka/Zhiliao](https://github.com/shatyuka/Zhiliao); upstream attribution and license are preserved.
