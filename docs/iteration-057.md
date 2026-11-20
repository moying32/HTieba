# 0.5.7 图片直接下载

按用户要求，将图片预览中的保存动作改为直接下载到系统公共`Download/HTieBa/`。目录自动创建，不再打开系统文件选择器。

- 保留服务端原文件、GIF格式与原有80MB下载限制，不导出预览位图。时间加随机后缀命名；重复下载生成新文件，不覆盖已有文件。
- Android10及以上使用MediaStore.Downloads的RELATIVE_PATH与IS_PENDING；写入完成后才发布，失败/取消删除未完成记录。无需照片读取权限或所有文件访问权限。
- Android8/9仅在首次点击时请求WRITE_EXTERNAL_STORAGE，manifest限制maxSdkVersion28；拒绝有明确提示，允许后直接写入相同公共目录并登记媒体。
- 下载期间禁用重复点击并显示进度，成功提示目录；下载/写入失败恢复按钮，清理缓存及未完成文件。关闭预览会取消当前任务。
- 原有液态玻璃、图片翻页/缩放、设置内预览画质不作改动。主题包导出仍为用户明确选择的导出流程。

新增文件复制测试覆盖自动建目录、原字节保持、禁止覆盖、取消和I/O失败清理。96项JUnit通过；发布Lint 0错误/44警告/2提示；R8发布构建及APK v2验签通过。

## 真机验证（2026-11-12）

PTP-AN00 / Android16保留数据覆盖安装0.5.7 / versionCode15。用户指定帖<redacted-id>的第一张图片，点击一次下载，没有跳到系统选择器，提示“已下载到 Download/HTieBa”。下载前目录不存在，点击后目录自动创建。

文件155479字节，SHA-256为`ad0cf63068d1704cee7892ceef1c56ac39d95a5b9f022291a8e1ef992956579a`，与此前独立读取的该服务端原图逐字节哈希一致。MediaStore登记为`relative_path=Download/HTieBa/`、`is_pending=0`、`mime_type=image/jpeg`。文件保留在手机供用户查看。

截图`.tools/checks/screenshots/v057-download-click.png`，文件核对`.tools/checks/v057-download-device.json`，构建汇总`.tools/checks/v057-evidence.json`。返回原帖及首页通过；账号与偏好保留，系统常亮保持0，未改变手机系统设置，已归还手机。当前进程最近500条日志无崩溃/ANR标记。没有进行贴吧账号写操作。

Android8/9权限授予/拒绝和媒体扫描、真实存储不足、网络中断及GIF下载未在本轮手机单独验证；原文件复制与失败/取消清理由电脑测试覆盖，不能将其扩展为所有系统版本均实机通过。
