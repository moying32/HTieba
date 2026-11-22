# 0.7.11-dev.3 源码与 APK 发布检查报告

- 时间：2026-11-22，Asia/Shanghai。
- 检查对象：github-upload 中的当前成果源码，版本 **0.7.11-dev.3 / versionCode 39**。
- 用途：准备公开仓库 moying32/HTieba 的后续 main 增量。已公开本地基线为 0e14af77c417b6adc653e0df993cc998b474e7db。
- **结论：PASS（本地源码准备与检查范围）**。结果绑定最终文件清单及 SHA-256；详细证据保存在发布目录之外。后续内容改变后须重新检查。

## 范围与局限

检查副本中的应用源码、资源、测试、构建配置与 Wrapper、工具、文档、设计原型、主题示例和授权声明。保留副本原有 Git 元数据及增强忽略规则，不带入父工作区历史。应用 src 下 139 个文件与当前原工作区逐字节一致；根构建声明和 Wrapper 也已核对。公开文档和设计验证脚本仅按需要作脱敏及可移植性修正。

工作区和已有 index 均经过技能规则扫描：505 次文本对象检查、0 扫描错误。138 次跳过为二进制及体积上限对象的工作区/index 重复计数；实际 69 个二进制/大文件均与既有公开基线逐字节一致。字体额外校验来源摘要和 OFL；Wrapper/主题归档额外检查 CRC 和成员路径。不以文本扫描推断二进制安全。

本次无新增本地提交，index 未修改；原有公共历史沿用已核验基线，未重新审计全部历史或联网刷新远端。最终工作区候选 Git blob 标识和 SHA-256 单独保存。用户仅要求本地整理与检查，因此不自动暂存、提交、推送或更新 Release；未来上传须按实际新增提交再核对远端与历史。

## 检查结果

| 检查项 | 结果与证据 |
| --- | --- |
| 凭据与私钥 | 通过。工作区/index 共15项规则命中已判定：Kotlin 返回标签、无效 URL 拒绝测试与示例域名、GitHub 公开页面样本的 SVG 小数坐标。新登录测试为 evil.test 域的无效地址；未发现需保留的真实凭据。规则不能保证发现全部秘密。 |
| 隐私与本机路径 | 通过。恢复历史样本、账号及设备标识脱敏，依据既有公开版本应用21个精确替换值；用户目录和附件位置改为通用说明。设计脚本使用 require('playwright') 及可选 CHROME_PATH，不带本机用户路径。原工作区代码及记录不修改。 |
| 临时文件 | 通过。实际副本不带环境文件、local.properties、签名私钥、APK/AAB、构建缓存、设备数据或原始日志。本轮验证输出和误带的 smoke 本机配置已移到发布目录外；不只依赖 gitignore。 |
| 体积及二进制 | 通过。69个二进制/大文件与公共基线一致；3个字体的 SHA-256 及原始许可存在性通过，2个归档 CRC 与成员路径通过。没有新增二进制资产。 |
| README 与配置说明 | 通过。展示当前 0.7.11-dev.3 / 39 开发源码，补充聊天/最简模式、返回导航及楼中楼开发记录入口；已发布版本下载入口保留其原版本含义。明确此快照未作为新版本发布，不把阅读能力认定为完整产品。 |
| 链接与工具语法 | 通过。全部 Markdown 相对文件链接无缺失；未分发本机产物链接改为交付说明。21个 Python/JavaScript 文件语法检查通过，未发现副本中陈旧源码文件。 |
| 许可证及第三方 | 通过。保留 GPL-3.0-only、Copyright (c) 2026 moying32、历史 MIT 范围及 Codex + ChatGPT 制作声明；不改写字体、jsoup MIT、Gradle 原始许可。依赖声明与公开基线不变，app 构建声明只变更版本/code；沿用既有直接及可获得传递依赖来源核查，不声称完整法律审计或 SBOM。 |
| 编译及单元测试 | 通过。在 github-upload 副本使用已有 SDK/Gradle 缓存离线运行 exportDebugTestClasspath 和 lintRelease；Gradle 退出0。动态发现全部 *Test.kt，JUnit 共267项通过，退出0。 |
| Lint | 通过。本轮实际 XML 报告为0错误、50警告、2提示；不以此改写历史安装包检查中的57警告，也不声称警告已全部修复。 |
| 拟推送历史 | 本轮不适用。HEAD 和 index 保持原基线，无新增提交或推送。后续上传必须重新核对届时实际新增 Git 对象及远端。 |

## 已执行变更

- 同步当前应用源文件、测试、构建版本、开发工具及最新开发/验收文档。
- 更新 README 和 GITHUB-UPLOAD.md，注明当前开发快照及本地准备状态；保留现有制作声明、GPL 与第三方边界。
- 清理副本中的个人路径、旧样本标识及失效本机产物链接；保留原工作区记录。
- 验证临时生成的 app/build、local.properties 及缓存均移出副本。详细日志、备份、清单和测试产物留在原工作区 work/github-preflight/source-dev3，不属于发布集合。

## 可复现检查

实际运行技能 scan_snapshot.py，对象为 github-upload，输出保存到副本外；使用已有 Gradle 8.13，以 --offline --no-daemon --console=plain 和外置 --project-cache-dir 执行 exportDebugTestClasspath、lintRelease；随后以导出的 classpath 执行 JUnitCore 的全部测试类。Python 用 ast.parse，JavaScript 用 node --check，字体与清单用 hashlib.sha256，归档用 zipfile.testzip。

完整命令及结果保留本地。未安装新依赖，未重跑全新环境 bootstrap、assembleRelease/R8、APK 签名或任何设备交互与账号写操作；旧版实机文档只说明其原有验收范围。

## 阻塞与已接受限制

**本地准备无阻塞。** 仍有50项 Lint 警告及2项提示。完整客户端、全部聊天/群写操作、多设备、无障碍和长期性能未全部验收；旧私信首次认证的 TLS 已知缺口继续保留。单台设备历史记录不代表完整功能通过，本次不分发新 APK。扫描不保证发现所有隐私或秘密。

## 前序本地准备状态

**仅本地准备完成，未暂存、未提交、未推送，未创建或更新 Release。** 未来实际上传前须复核届时源码、暂存集合、远端及新增历史；本报告不为之后未知变更自动放行。

## 本次发布授权与最终复核（2026-11-22）

用户现已明确授权将该版本源码推送 GitHub、发布 APK，并更新 README 和 Release 说明；本节取代前述仅本地准备的执行范围。目标为公开仓库 moying32/HTieba，main 增量和 v0.7.11-dev.3 开发预览 Release。远端 main 已重新读取，仍为 0e14af77c417b6adc653e0df993cc998b474e7db，没有同版本标签或 Release。

- 最终144份构建/测试输入与已验收APK的留档摘要一致；README和发布说明更新为本版改动、安装要求、GPL与第三方边界及真实验证范围。前轮267项JUnit与副本Lint结果沿用，因为应用输入没有改变。
- APK为原有HTieBa-0.7.11-dev.3.apk，26,124,994字节，SHA256为04526e1b08a8307d0156ca5e9a36c137f4b3347ed2a29ca54bc3d539a172ebf4。aapt确认包名dev.hydrogen.tieba、versionCode39、minSdk26/targetSdk36；v2验签通过，证书沿用已核验开发证书。
- ZIP完整性及DEX/assets/META-INF定向检查通过，未命中私钥、GitHub/AWS令牌、硬编码会话Cookie或本机用户目录模式，无环境/签名私钥/用户数据库文件。此规则检查不等于完整反编译审计。
- 构建和依赖配置除版本/code外与既有0.7.10公开基线一致。复用已按校验清单验证的第三方声明中149个保守运行时坐标及原始通知/元数据，并从当前APK重新提取字体、jsoup和内嵌通知；不是精确R8后SBOM。另附GPL全文LICENSE.txt，与源码LICENSE一致。
- 发布集合为已检查源码树，以及APK、第三方通知ZIP、SHA256SUMS.txt、LICENSE.txt、本报告五项Release附件；源码树不包含APK、签名私钥或原始设备记录。标签固定本次源码提交，不移动旧标签或修改旧Release。
- 推送前核对整个最终暂存树、相对已公开基线新增Git对象和报告；推送后逐项核对源码提交/标签、远程附件摘要、大小和状态。具体执行证据留在发布集合之外，不预先声称上传成功。

**本次发布准备结论：PASS。** 既定范围无未完成的必要准备项，用户已授权正常提交、推送和发布开发预览Release。应用功能限制及Lint警告保留，本次不重新构建或执行真实账号写入。

Final staged-snapshot verification: 329 files; 520 text checks; 16 classified findings (including the now-staged login test); 138 binary/size skips; 0 scan errors. All 144 archived APK inputs match. Static checks: 139 application files identical, 21 tool syntax checks passed, no missing relative links, 69 existing binary assets unchanged.
