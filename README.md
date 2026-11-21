<div align="center">

<img src="design/prototype/icon.svg" width="88" height="88" alt="氢贴吧图标" />

# 氢贴吧 · HTieba

**回到讨论本身。**

用 Kotlin 与 Jetpack Compose 重做贴吧的日常体验。<br>
紧凑信息流、原生阅读、液态玻璃，以及熟悉的吧、楼层和楼中楼。

![Android](https://img.shields.io/badge/Android-8.0%2B-42765C?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7867B5?style=flat-square&logo=kotlin&logoColor=white)
![开发状态](https://img.shields.io/badge/版本-0.7.6%20开发版-C09151?style=flat-square)

**[▶ 在 B 站观看介绍视频](https://www.bilibili.com/video/BV1rjsV6KEex/)**

[功能一览](#功能一览) · [构建与运行](#构建与运行) · [开发文档](#开发文档)

</div>

---

## 关于氢贴吧

氢贴吧是一个开发中的 Android 第三方贴吧客户端，目标是去除广告、重做原生 UI，并逐步覆盖浏览、发布、互动、关系、消息与账号，成为可以日常使用的完整客户端。

信息架构参考 X：把内容放在前面，让推荐、关注、发布与消息更容易触达。同时保留贴吧自己的社区结构与交流方式。推荐内容与排序来自百度贴吧接口，不另建本地推荐算法。

> **当前为开发版。** 版本号保留为 0.7.6，源码另含群消息类型 81 的后续修复，与历史 0.7.6 安装包不完全相同。部分能力已实现，但尚未完成真实账号或完整设备验收，详见[功能验收台账](docs/feature-status.md)。

## 功能一览

| 体验 | 已有能力 |
| --- | --- |
| **刷帖与发现** | 推荐信息流、关注吧、搜索与热榜、最近访问、下拉刷新和分页 |
| **阅读与讨论** | 原生帖子详情、回复与楼中楼、只看楼主、跳页、评论定位与用户主页 |
| **图片与视频** | 多图连看、双指缩放、原图下载、浮岛视频播放器、倍速与全屏 |
| **收藏与社区** | 本地收藏、云收藏接口、关注与粉丝列表、吧资料、管理我的吧与一键签到 |
| **主题与外观** | 经典、映间 MARGIN、Claude 风；自定义主题色、浅深模式、字体与界面倍率 |
| **交互与材质** | 液态玻璃悬浮导航、跟手滑动、按压反馈、减弱动效与材质开关 |

发布、点赞、评论、关系管理、私信及群聊也已接入部分原生流程。**接口接入不等于真实使用验收通过**，以下内容仍在推进：

- 图文新帖、精确子回复、云收藏及资料修改等写操作的完整验证。
- 私信与群聊的真实收发、完整历史、媒体发送、云未读与后台通知。
- 完整账号登录、失效与退出流程，以及更多设备、无障碍与长期使用验证。
- 富媒体发布、历史帖编辑和高级社区管理。

后续范围与完成门槛见[开发路线](docs/roadmap-1.0.0.md)和[原版功能差异](docs/official-feature-comparison.md)。

## 构建与运行

仓库当前提供源码，APK 不随源码目录分发。已有构建脚本面向 **Windows / PowerShell**。

| 环境 | 要求 |
| --- | --- |
| Java | JDK 21 |
| Python | 本机已安装 Python；本轮检查使用 3.14.7 |
| Android SDK | Platform 36、Build Tools 35.0.0、Platform Tools |
| Gradle | 8.13，由环境引导脚本准备，Wrapper 固定版本及摘要 |
| 系统兼容 | 最低 Android 8.0（API 26），目标 API 36 |

先配置 `JAVA_HOME` 和 `ANDROID_HOME`，指向已安装的 JDK 与 Android SDK，然后在项目根目录运行：

```powershell
python tools/bootstrap.py
python tools/bootstrap.py --check
python tools/build.py release
```

`bootstrap.py` 会准备 Gradle、JADX 与独立 Python 环境中的 mitmproxy，首次执行需要网络；JDK 与 Android SDK 需预先安装。

构建输出为 `app/build/outputs/apk/release/app-release.apk`。当前发布构建启用 R8 和资源压缩，使用开发测试签名；正式分发需要独立签名及版本管理。

连接一台已授权的 Android 设备后，可安装并启动：

```powershell
python tools/device.py install --release
python tools/device.py launch
```

启动后可浏览推荐信息流，通过吧名搜索或帖子链接进入阅读；需要账号的操作通过百度官方页面登录。

<details>
<summary><b>开发、验证与环境说明</b></summary>

```powershell
python tools/build.py app      # 调试构建
python tools/build.py check    # 单元测试与发布版 Lint
python tools/build.py smoke    # 独立环境检查应用
python tools/device.py info    # 设备信息
```

环境与缓存位于 `.tools/`，不纳入版本管理。Google Maven 当前配置使用阿里云镜像，Maven Central 和 Gradle 使用官方源。具体构建及设备工具见 [`tools/`](tools/)。

2026-11-20 的源码快照检查通过 **204 项单元测试**，完整发布版 Lint 为 **0 错误、42 警告、2 提示**。这是当次检查结果，不能替代后续版本的构建或真机验收。检查记录见仓库中的 `OPEN_SOURCE_CHECK.md`。

设计稿可离线打开；运行 Node 验证脚本需另行准备 Playwright 及 Chromium，也可通过 `CHROME_PATH` 指定本机浏览器。

</details>

## 主题与个性化

在「我的 → 设置 → 外观与主题」中切换风格、主题色、字体和浅深模式，也可独立关闭玻璃效果或减弱动效。支持 `.h2theme` 主题包的导入、预览、切换和删除。

[主题制作教程](app/src/main/assets/theme-guide.md) · [AI 提示词](app/src/main/assets/theme-ai-prompt.txt) · [主题 JSON 示例](examples/theme.json) · [示例主题包](examples/paper-sky.h2theme)

```powershell
python tools/theme_pack.py examples/theme.json examples/paper-sky.h2theme
```

## 开发文档

| 想了解什么 | 从这里开始 |
| --- | --- |
| 产品范围与阶段目标 | [产品要求](docs/product-requirements.md) · [开发路线](docs/roadmap-1.0.0.md) |
| 功能与真实验证情况 | [验收台账](docs/feature-status.md) · [真机记录](docs/device-validation.md) |
| 设计原则与原生实现约定 | [设计与交互规范](docs/design-guidelines.md) |
| 接口与开发状态 | [开发说明](docs/development-status.md) · [原版功能差异](docs/official-feature-comparison.md) |
| 本版与后续修复 | [0.7.6 记录](docs/iteration-076.md) · [消息类型 81 修复](docs/message-type-81-fix.md) |
| 参考项目与依赖来源 | [项目与开发者清单](docs/外部项目与开发者清单.md) · [直接构建依赖](docs/直接构建依赖清单.csv) |

源码主要位于 `app/src/main/java/dev/hydrogen/tieba/`：`data/` 负责协议、模型与存储，`ui/` 负责 Compose 界面与状态；`design/` 保存交互原型，`examples/` 保存主题示例。

## 账号与数据

账号密码在百度官方 HTTPS 页面输入，原生请求使用应用自身 WebView 会话中的 Cookie；不读取官方贴吧应用的私有数据。**旧版私信首次认证仍存在缺少 HTTPS 保护的已知限制**，详见[私信接入记录](docs/iteration-071.md)。

本地收藏与百度云收藏分别保存。写操作仅在服务端明确确认后更新状态，评论请求不会自动重试。当前账号与消息能力仍有验证缺口，请结合验收台账了解具体范围。

## 致谢与授权

感谢 [Emil Kowalski](https://github.com/emilkowalski/skills) 的设计工程原则，以及映间 MARGIN、aiotieba、TiebaLite 等项目提供的视觉与协议研究参考。具体使用关系见[参考来源清单](docs/外部项目与开发者清单.md)。

本仓库当前用于**公开托管源码，项目自身暂未授予开源许可**，未选择根 `LICENSE`。第三方组件与字体继续遵循各自授权，字体来源及许可见 `app/src/main/assets/font_licenses/`；上传副本另保留 Gradle 的第三方通知。

---

<div align="center">

**氢贴吧 · 让内容留在眼前，让界面退到身后。**

[观看 B 站视频](https://www.bilibili.com/video/BV1rjsV6KEex/)

</div>
