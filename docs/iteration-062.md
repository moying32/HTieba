# 0.6.2 收藏数与吧页面返回

2026-11-14，versionCode19。

| Before | After | Why |
| --- | --- | --- |
| 接口缺少收藏数时显示“—”，无障碍描述提示未提供 | 只保留百度收藏星形按钮，不生成数字占位；有真实值时继续显示，包括0 | 遵循用户要求，不展示无效统计 |
| 打开吧页面强制切到逛吧，只有帖子详情/面板来源入栈 | 所有吧页面入口保存来源，保留原主栏目；返回恢复帖子、列表、搜索、个人页或原吧 | 吧是来源页面的下一级，不改变来源栏目 |

改动沿用现有SurfaceReturn，保存原帖子内容、分页/目标回复、面板、吧列表、收藏分类与排序；Compose既有SaveableStateProvider继续保存原页面滚动状态。页面内返回按钮和系统返回均调用同一closeForum。吧资料面板先关闭自身，再返回来源；原玻璃、导航与图片动效不改。

## 构建

98项JUnit通过；发布Lint为0错误、46警告、2提示。检查日志`.tools/checks/v062-check.log`。发布R8与APK v2验签通过，日志`.tools/checks/v062-release.log`。

安装包`apks/HTieBa-0.6.2.apk`，25,888,835字节，SHA-256：`12D3619FAE78A208510E8D3887ECC8B88A85B7C5BC700C61904C138D707A335F`。已保留数据覆盖安装到手机。

## 验收

更新前已在PTP-AN00 / Android16只读复现：首页帖子卡片点孙笑川吧，系统返回落在逛吧根页。截图`v062-before-home`、`v062-before-forum`、`v062-before-forum-back`，位于`.tools/checks/screenshots/`。用户授权本轮只读操作，不点赞、收藏或发送内容。

| 场景 | 修复包观察与截图 |
| --- | --- |
| 缺失与真实收藏数 | 首页缺失时只有星形按钮，无数字/“—”；浏览历史真实13、16照常显示。`v062-installed-home`、`v062-history-before` |
| 首页卡片吧名 → 社区 → 返回 | 从日本动漫吧关闭资料弹层仍停在该吧，页面返回按钮恢复原首页与卡片位置。`v062-home-to-forum`、`v062-forum-info`、`v062-forum-info-dismissed`、`v062-forum-ui-back-home` |
| 帖子详情 → 社区 → 返回 | 示例帖<redacted-id>向下滚动后进入deepseek吧，在社区加载中系统返回，恢复原帖，三张可见图片的bounds与进入前一致。`v062-thread-position-before`、`v062-thread-to-forum`、`v062-forum-system-back-thread` |
| 搜索 → 社区 → 返回 | 读取已有Android历史的真实吧结果，直达Android吧后系统返回，输入Android、原吧分类和结果列表保留。`v062-search-results`、`v062-search-to-forum`、`v062-forum-back-search` |
| 个人页浏览历史 → 社区 → 返回 | 历史列表点deepseek吧后系统返回恢复浏览历史，页面返回恢复个人页。`v062-history-before`、`v062-history-to-forum`、`v062-forum-back-history`、`v062-history-back-profile` |

原字号100%、界面75%、主题与动效偏好未改。结束停在首页，充电常亮由临时7恢复原值0；当前进程最后500行日志无FATAL EXCEPTION/ANR标记。无真实账号写操作；阅读更新本地浏览记录，使用已有Android搜索会更新本地历史顺序。手机已交还用户。汇总`.tools/checks/v062-evidence.json`、系统记录`v062-final-system.json`。

本轮没有重复验收上一版的图片/玻璃动效、全部主题/字号和系统版本，也未专项覆盖多层跨吧嵌套、进程终止后的返回栈恢复或完整无障碍。真实0收藏数的存储兼容继续由原测试覆盖，本轮未取得真机0计数样本；不据此声称全场景回归完成。
