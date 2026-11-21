# 第三方来源与通知

项目自身代码采用 [MIT 协议](../LICENSE)，版权署名为 Copyright (c) 2026 moying32。第三方组件、字体及其原始版权声明继续适用各自原有授权，不因项目采用 MIT 而变更。

- `gradle/LICENSE` 和 `gradle/NOTICE` 原样保留自本机用于构建的官方 Gradle 8.13 分发包，供随源码分发的 Wrapper 脚本和 JAR 参考。该 NOTICE 覆盖完整 Gradle 分发包，不表示其中列出的所有组件均随此源码快照分发。
- 三款内置字体的原始授权、来源和文件 SHA-256 见 `app/src/main/assets/font_licenses/`。
- Android/Compose、网络、图片、媒体等直接依赖由 Gradle 下载，本快照不分发其 AAR/JAR 或已编译 APK。声明清单见 `docs/直接构建依赖清单.csv` 和 `app/build.gradle.kts`；实际依赖关系由 Gradle 解析。
- 设计稿 README 将头像、示例消息、SVG 插画与程序生成山水媒体标明为本地演示，截图为这些原型的截图；本快照不包含手机账号截图或真实聊天导出。
- 协议研究、产品和设计参考见 `docs/外部项目与开发者清单.md`。研究快照未随源码分发；参考项目不自动成为应用运行时依赖，其许可证也不自动成为本项目许可证。

将来发布 APK、引入上游源码或新增素材时，应按实际分发集合重新核对来源和通知；本文件不是最终二进制的完整 SBOM。

0.7.10新增jsoup 1.21.2（MIT），授权保留于`app/src/main/assets/third_party_licenses/jsoup.txt`。APK Release另附从实际releaseRuntimeClasspath及原始依赖归档收集的第三方通知包；保守依赖清单不等于R8后精确类清单。
