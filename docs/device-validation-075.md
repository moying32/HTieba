# 0.7.5 真机只读验收

2026-11-15；PTP-AN00 / Android16，1264×2800。用户授权实机验收，本轮不发送消息、不申请入群、不撤回、不禁言或解禁。由设备原有0.7.1（25）保留数据覆盖安装0.7.5（29），账号及本机最近群保留。包哈希仍为0.7.5交付记录中的值，未修改应用源码。

## 已观察结果

| 项目 | 本轮结果 | 证据（工作区相对路径） |
| --- | --- | --- |
| 安装、启动和首页/消息切换 | 通过 | `.tools/checks/v075-device-install.log`、`screenshots/v075-start.*`、`screenshots/v075-final-home.xml` |
| 群资料读取 | 样本群<redacted-id>，所属吧、8位成员、Lv.2门槛、当前账号禁言状态和机器人资料可读；公告缺失明确提示 | `screenshots/v075-group-details.*`、`screenshots/v075-details-reopen.xml` |
| 非空禁言名单 | 读取2/2条，头像、昵称和百度原样剩余时长显示正常；重复打开可重新读取 | `screenshots/v075-management.*`、`screenshots/v075-robot-target-visible.*` |
| 已加载名单筛选 | 无匹配提示正常，清空恢复名单 | `screenshots/v075-filter-result.*`、`screenshots/v075-filter-cleared.xml` |
| 从发言者选择目标 | 能打开选择列表、预填UID并尝试资料读取；本轮两个样本均返回220012，错误后没有开放管理动作 | `screenshots/v075-speakers.*`、`screenshots/v075-target.*`、`screenshots/v075-robot-target-visible.*` |
| 群共享图片、连续图库与定位 | 已加载8张；打开后8/8，上一张7/8；系统返回回到资料，定位原消息回到聊天并显示目标图片 | `screenshots/v075-group-gallery.*`、`screenshots/v075-gallery-preview.*`、`screenshots/v075-gallery-previous.xml`、`screenshots/v075-message-location.*` |
| 资料面板关闭/重开 | 可以返回聊天并重开；但键盘优先返回存在下述问题 | `screenshots/v075-back-repro.xml`、`screenshots/v075-details-second.xml` |
| 私信同步 | 同步完成但窗口为空，仅验证连接/空态，不能证明非空解析或双向收发 | `screenshots/v075-inbox.*` |

上述截图/层级均在`.tools/checks/screenshots/`。表中`screenshots/`为该目录的简写，未将读取接口成功当作管理权限或真实写入成功。

## 发现的问题

1. **P1：部分群消息不识别。** 真实历史出现多条`[暂不支持的消息类型 81]`及默认身份。现有解析未还原其内容；尚无原始字段结构证据，不能判定它们是文字、系统事件或撤回。
2. **P1：目标资料成功路径受阻。** 历史发言者与群资料机器人UID两次读取均返回“您访问的用户已被封禁（贴吧错误码220012）”。本轮只能证明错误可见与提交受阻，不能据此断定两个账号均确已封禁，也未验证成功核对、时长和确认框的实际布局。
3. **P2：键盘返回关闭整张群资料面板。** 管理页UID或筛选框获得焦点后，按系统返回会连同面板关闭。期望先隐藏键盘，下一次返回再关闭面板。已复现两次；输入法自身的收起键可保留面板。
4. **待观察：群首次同步一次连接中断。** 消息页出现`unexpected end of stream`，随后重新进入群可读资料和历史。未做弱网或长期测试，不判定网络稳定性通过。

## 未覆盖与结束状态

没有测试非空私信、真实发言/入群/撤回/禁言/解禁；没有切换账号或核实吧务身份，没有群空名单/权限拒绝/多页名单样本。未测试断网、TalkBack、浅深主题矩阵、50–150%倍率矩阵和长期性能。未用假数据填补这些验收项。

本轮未更改主题、倍率或系统亮屏参数；临时UID/筛选状态随面板关闭清空，聊天输入未填写。结束回到刷帖首页。充电常亮保持0，锁屏超时保持3600000毫秒。当前应用PID最近1000行日志中`FATAL EXCEPTION`、`ANR in`及`OutOfMemoryError`标记均为0，仅反映该段日志。设备临时层级文件已清理。设备前后状态见`.tools/checks/v075-device-before.json`、`.tools/checks/v075-device-final.json`。

**结论：部分只读能力通过，0.7.5未达到群管理完整验收通过。** 后续优先处理消息类型81、目标资料读取和键盘返回，再使用明确授权的群/目标账号验证写入闭环。
