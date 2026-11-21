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

用户已于 2026-11-21 明确选择 MIT 开源协议，项目授权全文见根目录 [LICENSE](LICENSE)，版权署名为 Copyright (c) 2026 moying32。字体原有授权及 Gradle 分发包通知随源码保留；第三方组件继续遵循各自授权。此前检查报告中的未授权状态为历史记录，以最新 MIT 授权决定为准。

## 0.7.10同步

本次上传当前完整客户端源码及测试，更新说明见`docs/iteration-0710.md`。应用构建输入与本机发布包所用源码逐文件核对；本机签名私钥、环境及账号数据不上传。APK及第三方通知作为Release附件分发，源码仓库继续不跟踪APK。
