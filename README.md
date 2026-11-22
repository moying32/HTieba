<div align="center">

<img src="design/prototype/icon.svg" width="88" height="88" alt="氢贴吧图标" />

# 氢贴吧 · HTieba

**回到讨论本身。**

用 Kotlin 与 Jetpack Compose 重做贴吧的日常体验。<br>
紧凑信息流、原生阅读、液态玻璃，以及熟悉的吧、楼层和楼中楼。

![Android](https://img.shields.io/badge/Android-8.0%2B-42765C?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7867B5?style=flat-square&logo=kotlin&logoColor=white)
![开发状态](https://img.shields.io/badge/版本-0.7.11--dev.3%20开发源码-C09151?style=flat-square)

**[▶ 在 B 站观看介绍视频](https://www.bilibili.com/video/BV1rjsV6KEex/)**

[功能一览](#功能一览) · [构建与运行](#构建与运行) · [开发文档](#开发文档)

</div>

---

## 关于氢贴吧

氢贴吧是一个开发中的 Android 第三方贴吧客户端，目标是去除广告、重做原生 UI，并逐步覆盖浏览、发布、互动、关系、消息与账号，成为可以日常使用的完整客户端。

信息架构参考 X：把内容放在前面，让推荐、关注、发布与消息更容易触达。同时保留贴吧自己的社区结构与交流方式。推荐内容与排序来自百度贴吧接口，不另建本地推荐算法。

> **当前源码为 0.7.11-dev.3（versionCode 39）。** 在 0.7.10 基础上加入聊天读取与未看状态修复、最简模式、登录自动完成、返回导航及楼中楼排版改进；本版本作为开发预览版提供源码与 APK，功能仍在持续验收。变更及验收范围见[0.7.11记录](docs/iteration-0711.md)、[返回导航](docs/back-navigation-0711.md)和[楼中楼排版](docs/floor-density-0711.md)。完整客户端验收仍在推进，具体范围见[功能验收台账](docs/feature-status.md)。

## 功能一览

| 体验 | 已有能力 |
| --- | --- |
| **刷帖与发现** | 推荐信息流、关注吧、搜索与热榜、最近访问、下拉刷新和分页 |
| **阅读与讨论** | 原生帖子详情、回复与楼中楼、只看楼主、跳页、评论定位与用户主页 |
| **图片与视频** | 多图连看、双指缩放、原图下载、浮岛视频播放器、倍速与全屏 |
| **收藏与社区** | 本地收藏、云收藏接口、关注与粉丝列表、吧资料、管理我的吧与一键签到 |
| **主题与外观** | 经典、映间 MARGIN、Claude 风；自定义主题色、浅深模式、字体与界面倍率 |
| **交互与材质** | 液态玻璃悬浮导航、跟手滑动、按压反馈、减弱动效与材质开关 |
| **应用更新** | 启动后台检查、设置手动检查、跳过当前版本、下载进度与系统确认安装 |

发布、点赞、评论、关系管理、私信及群聊也已接入部分原生流程。**接口接入不等于真实使用验收通过**，以下内容仍在推进：

- 图文新帖、精确子回复、云收藏及资料修改等写操作的完整验证。
- 私信与群聊的真实收发、完整历史、媒体发送、云未读与后台通知。
- 完整账号登录、失效与退出流程，以及更多设备、无障碍与长期使用验证。
- 富媒体发布、历史帖编辑和高级社区管理。

后续范围与完成门槛见[开发路线](docs/roadmap-1.0.0.md)和[原版功能差异](docs/official-feature-comparison.md)。

## 0.7.11-dev.3 开发预览版

[查看 Release 与更新说明](https://github.com/moying32/HTieba/releases/tag/v0.7.11-dev.3) · [下载 APK](https://github.com/moying32/HTieba/releases/download/v0.7.11-dev.3/HTieBa-0.7.11-dev.3.apk)

- **聊天与登录**：分离群资料和历史读取，补充旧资料接口的备用读取；按可见消息推进本机未看状态，清理撤回内容，支持官方临时登录页验证成功后自动返回。
- **最简模式**：提供低动效、无玻璃材质和静态图片加载选项，保留原有外观偏好，关闭后恢复。
- **返回导航**：改进右边缘跟手、取消与连续返回；键盘和局部弹层优先处理系统返回，减弱动效/最简模式遵循无过渡策略。
- **楼中楼排版**：合并作者与操作行、释放正文宽度，并针对大字号、深色及操作区域作单台设备验收。

版本号 **0.7.11-dev.3 / 39**，Android 8.0+；使用沿用的开发测试签名。源码副本检查为267项JUnit通过、Lint 0错误/50警告/2提示；历史APK构建记录为57警告/2提示，二者范围分别保留。当前标签源码的144份构建/测试输入与此APK留档一致，不承诺逐字节可复现构建。完整聊天收发、群管理写操作、多设备和长期性能仍未全部验收；旧私信首次认证的TLS缺口仍存在。

## 构建与运行

源码位于main分支，安装包从[GitHub Releases](https://github.com/moying32/HTieba/releases)下载；APK不纳入源码版本管理。已有构建脚本面向 **Windows / PowerShell**。

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

**更新功能从0.7.9开始提供，需要先手动安装一次。** 后续从 `moying32/HTieba` 的GitHub Releases检查新版本，下载后由用户在系统确认安装；发布者须提供同签名、递增版本号的APK附件。使用方式与发布规则见[应用内更新](docs/app-updates.md)。

<details>
<summary><b>开发、验证与环境说明</b></summary>

```powershell
python tools/build.py app      # 调试构建
python tools/build.py check    # 单元测试与发布版 Lint
python tools/build.py smoke    # 独立环境检查应用
python tools/device.py info    # 设备信息
```

环境与缓存位于 `.tools/`，不纳入版本管理。Google Maven 当前配置使用阿里云镜像，Maven Central 和 Gradle 使用官方源。具体构建及设备工具见 [`tools/`](tools/)。

2026-11-21反馈整改源码检查通过 **210项单元测试**，完整发布版Lint为 **0错误、51警告、2提示**。这不替代真机验收，详见[本轮记录](docs/feedback-fixes-20261121.md)。`OPEN_SOURCE_CHECK.md`保留2026-11-20的历史开源检查结论，不代表本轮已重新进行上传前检查。

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
| 本版与后续修复 | [0.7.11开发记录](docs/iteration-0711.md) · [返回导航](docs/back-navigation-0711.md) · [楼中楼排版](docs/floor-density-0711.md) · [0.7.10限流修复](docs/iteration-0710.md) · [0.7.9应用内更新](docs/iteration-079.md) · [0.7.8修复与实机复测](docs/iteration-078.md) · [0.7.7反馈整改](docs/feedback-fixes-20261121.md) · [消息类型81修复](docs/message-type-81-fix.md) |
| 参考项目与依赖来源 | [项目与开发者清单](docs/外部项目与开发者清单.md) · [直接构建依赖](docs/直接构建依赖清单.csv) |

源码主要位于 `app/src/main/java/dev/hydrogen/tieba/`：`data/` 负责协议、模型与存储，`ui/` 负责 Compose 界面与状态；`design/` 保存交互原型，`examples/` 保存主题示例。

## 账号与数据

账号密码在百度官方 HTTPS 页面输入，原生请求使用应用自身 WebView 会话中的 Cookie；不读取官方贴吧应用的私有数据。**旧版私信首次认证仍存在缺少 HTTPS 保护的已知限制**，详见[私信接入记录](docs/iteration-071.md)。

本地收藏与百度云收藏分别保存。写操作仅在服务端明确确认后更新状态，评论请求不会自动重试。当前账号与消息能力仍有验证缺口，请结合验收台账了解具体范围。

## 制作声明

本项目自身的代码、界面设计、文档及其他原创内容均由 **Codex + ChatGPT** 制作，项目需求与发布由维护者组织。第三方依赖、字体及引用内容的来源与版权归其原作者所有，详见下方致谢和第三方声明。

## 致谢与授权

感谢 [Emil Kowalski](https://github.com/emilkowalski/skills) 的设计工程原则，以及映间 MARGIN、aiotieba、TiebaLite 等项目提供的视觉与协议研究参考。具体使用关系见[参考来源清单](docs/外部项目与开发者清单.md)。

本项目自身代码自本次许可变更起采用 **[GNU GPL v3.0 开源协议](LICENSE)**（`GPL-3.0-only`，仅第 3 版），版权署名为 Copyright (c) 2026 moying32。此前以 MIT 发布版本的授权记录继续保留，具体范围见[授权说明](docs/licensing.md)。第三方组件与字体继续遵循各自授权；字体来源及许可见 [`app/src/main/assets/font_licenses/`](app/src/main/assets/font_licenses/)；GitHub 发布副本另保留其他第三方来源与通知。

---

<div align="center">

**氢贴吧 · 让内容留在眼前，让界面退到身后。**

[观看 B 站视频](https://www.bilibili.com/video/BV1rjsV6KEex/)

</div>
