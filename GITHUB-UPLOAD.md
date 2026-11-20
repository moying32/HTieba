# GitHub 上传说明

本文件夹是当前工作区的源码快照，版本号为 0.7.6。将本文件夹内的内容作为 GitHub 仓库根目录上传，保留点开头的 `.gitignore` 文件。

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

本次仅整理本地文件，没有创建远程仓库或执行上传。文件夹是独立副本，后续修改原项目不会自动同步到这里。

按用户决定公开托管源码，暂不授予项目自身开源许可。原项目根目录未提供 LICENSE，本次没有代选开源许可证；字体原有授权及 Gradle 分发包通知随源码保留。
