# 9.5.0虚构版本的更新检测测试

2026-11-21。用户明确要求在 `apks/test` 新建只显示“仅测试”的9.5.0氢帧APK，并上传GitHub Release用于测试自动检测。本次授权仅用于这一测试预发布，不代表将产品路线或主源码版本升级为9.5.0。

## 已完成

- 独立原生Android小项目位于 `apks/test/source`；包名 `dev.hydrogen.tieba`，versionName `9.5.0`，versionCode34。应用名按用户要求为“氢帧”，唯一TextView内容为“仅测试”，支持系统浅深色，没有网络权限或第三方运行时依赖。
- 构建与Lint退出0，Lint0错误/2非阻塞警告；同当前0.7.9开发测试签名，v2验签通过。仅做静态/包内容核验，没有安装运行空壳，不把界面源码检查当成实机显示通过。
- APK：`apks/test/HTieBa-9.5.0.apk`，8,012字节；SHA256 `CD0275192C7339465D63FFE7338BE13951D32FFC50E2ACC89B0323B691D0F16E`。
- 公开发布：[v9.5.0 · 仅测试](https://github.com/moying32/HTieba/releases/tag/v9.5.0)，2026-11-21 18:48:40 Asia/Shanghai；Release ID453620054、附件ID775250349。先上传草稿核对后发布，prerelease=true、draft=false，未指定为latest。
- 标签指向既有公开main提交 `34e48bf11f41e2dc58a11f48384acff5e833025c`；未推送新源码或提交。Release说明明确APK由独立最小项目构建，tag不表示主源码升级为9.5.0。
- 实际上传只有APK；工作区、本机SDK配置、私钥、日志、扫描报告、源码和账号数据均未上传。GitHub返回的附件摘要/大小与本地一致，匿名公开下载HTTP200并逐字节一致。
- 直接用现有生产 `ReleaseUpdates.parse` 解析实时GitHub发布记录，正确选中9.5.0预发布、标准APK名、8,012字节和摘要，并判定高于0.7.9。该发布记录通过已登录GitHub CLI读取，不能冒充匿名API或手机网络成功。
- 主应用128份构建输入与最终0.7.9快照完全一致，0.7.9交付包摘要未变。

## 限制与后续

匿名API仍返回403限流，无法确认手机当前网络能取得新版；手机18:49处于B站前台，保留其现场，没有切换应用或安装测试空壳。因此本次已完成发布、公开附件下载及生产解析器检测，设备实际启动提示、跳过、应用内下载和系统确认安装仍待验。

9.5.0只是测试高版本，现有客户端包含预发布，会向所有未跳过此版的用户提示。本包安装后替换客户端功能，且没有自更新入口；仅验证检测时无需安装。恢复旧客户端可能需要系统允许降级，卸载会删除应用数据。Release说明和本地README都已写明这些影响。本次未删除Release或tag；测试结束后的处理需要后续指示。

上传前检查报告位于 `apks/test/OPEN_SOURCE_CHECK.md`。本地证据位于 `.tools/checks/`：`update950-test-build.log`、`update950-package-evidence.json`、`update950-asset-scan.json`、`update950-published-release.json`、`update950-public-access.json`、`update950-production-parser.json`。扫描无命中不代表不存在未知风险，二进制由专项核验补充；没有新增提交，不将此检查视为既有仓库历史审计。
