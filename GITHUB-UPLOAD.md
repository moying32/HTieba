# GitHub 上传说明

本文件夹是当前工作区的源码快照，版本号为 0.7.10 / versionCode 35。将本文件夹内的内容作为 GitHub 仓库根目录上传，保留点开头的 `.gitignore` 文件。

包含 Android 应用源码与测试、Gradle Wrapper、构建和设备工具、项目文档、设计原型、主题示例及随包字体授权文件。

未包含原仓库 `.git`、本机 `local.properties`、`.tools`、Gradle/Kotlin 缓存、构建产物、APK/AAB、签名密钥、设备截图、宣传视频和本地工作记录。指向未分发安装包的 Markdown 链接已改为本机交付说明；历史文档中的其他本机证据路径仅保留作记录。

## 构建

从仓库根目录运行 README 中的 `python tools/bootstrap.py`，生成本机环境，再按 README 构建。未保留本机 SDK 绝对路径。此次上传前验证的命令、结果和边界见 `OPEN_SOURCE_CHECK.md`。

## 使用 Git 上传

在本文件夹打开终端，替换下面的仓库地址后执行：

```powershell
git init
git add .
git commit -m "Initial import"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPOSITORY.git
git push -u origin main
```

本副本已上传至公开仓库 `moying32/HTieba` 的 `main` 分支。文件夹是独立副本，后续修改原项目不会自动同步到这里。

用户于 2026-11-22 明确将项目自身代码许可从 MIT 改为 GNU GPL v3.0（`GPL-3.0-only`，仅第 3 版），全文见根目录 [LICENSE](LICENSE)，版权署名沿用 Copyright (c) 2026 moying32。此前以 MIT 发布的版本继续保留原有授权记录；历史版本及第三方组件范围见[授权说明](docs/licensing.md)。字体授权和 Gradle 原始通知保持不变。

## 0.7.10同步

本次上传当前完整客户端源码及测试，更新说明见`docs/iteration-0710.md`。应用构建输入与本机发布包所用源码逐文件核对；本机签名私钥、环境及账号数据不上传。APK及第三方通知作为Release附件分发，源码仓库继续不跟踪APK。

## 当前开发成果准备（2026-11-22）

本次将 0.7.11-dev.3 / 39 当前成果同步到此独立副本，仅本地整理和检查，不自动暂存、提交、推送或创建 Release。README 展示当前源码状态，已发布版本的下载入口保留其原版本含义。原始账号/设备记录、APK、签名私钥和构建缓存均不作为源码交付；检查结论见 OPEN_SOURCE_CHECK.md。

## 0.7.11-dev.3 发布

用户已授权将当前已检查源码推送到 main，并发布 v0.7.11-dev.3 开发预览 Release。本次使用原有 HTieBa-0.7.11-dev.3.apk（versionCode 39），不改名冒充稳定版或重新构建。标签绑定本次源码提交，144份APK留档输入与待发布源码一致；README 与 Release 说明列出本版改动及验收限制。APK、校验、GPL全文和第三方通知仅作为Release附件，源码树不跟踪APK。此前“仅本地准备”段落为本次授权之前的历史记录。
