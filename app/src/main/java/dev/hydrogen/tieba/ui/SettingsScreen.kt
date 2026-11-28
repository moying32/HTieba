package dev.hydrogen.tieba.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.ThemeMode
import dev.hydrogen.tieba.data.VisualStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.hydrogen.tieba.ui.theme.HydrogenTheme

@Composable
fun SettingsScreen(model: TiebaViewModel, modifier: Modifier) {
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    var logout by rememberSaveable { mutableStateOf(false) }
    var appearancePage by rememberSaveable { mutableStateOf<String?>(null) }
    var instantBack by remember { mutableStateOf(false) }
    fun openPage(page: String) { instantBack = false; appearancePage = page }
    fun back(gesture: Boolean = false) {
        instantBack = gesture
        appearancePage = settingsParent(appearancePage)
    }
    val motion = LocalMotionEnabled.current
    val backGesture = Modifier.backGesture(appearancePage != null && model.settingsOpen, motion, appearancePage) { back(it) }
    val pageContent: @Composable (String?) -> Unit = { page ->
        when (page) {
            "fonts" -> FontSettings(model) { back() }
            "themes" -> ThemeManager(model, { back() }, { openPage("guide") })
            "guide" -> ThemeGuide(model) { back() }
            "filters" -> ContentFilterSettings(model) { back() }
            else -> Column(Modifier.fillMaxSize()) {
                val title = when (page) {
                    "appearance" -> "外观与主题"; "size" -> "字体与界面"; "media" -> "阅读与媒体"
                    "interaction" -> "交互与动效"; "account" -> "账号"; "about" -> "关于氢贴吧"; "updates", "about-updates" -> "软件更新"; else -> "设置"
                }
                ScreenHeading(title, onBack = { if (page == null) model.settingsOpen = false else back() })
                key(page) {
                Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 28.dp)) {
                    when (page) {
                        null -> {
                            SettingsEntry("外观与主题", "风格、主题色、推荐页布局") { openPage("appearance") }
                            SettingsEntry("字体与界面", "字体、字号与界面 50–150%") { openPage("size") }
                            SettingsEntry("阅读与媒体", "图片精度、徽章、内容过滤与缓存") { openPage("media") }
                            SettingsEntry("交互与动效", "最简模式、液态玻璃、减弱动效") { openPage("interaction") }
                            SettingsEntry("账号", model.account.name.ifBlank { "登录百度账号" }) { openPage("account") }
                            SettingsEntry("关于氢贴吧", "版本与内容来源") { openPage("about") }
                            SettingsEntry("检查更新", "当前 ${dev.hydrogen.tieba.BuildConfig.VERSION_NAME} · 启动时自动检查") { openPage("updates") }
                        }
                        "appearance" -> {
                            StylePicker(model)
                            SectionLabel("推荐页正文行数")
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (2..6).forEach { lines ->
                                    BubbleFilterChip(selected = model.recommendationLines == lines,
                                        onClick = { model.changeRecommendationLines(lines) }, label = { Text("$lines 行") },
                                        modifier = Modifier.heightIn(min = LocalTouchTarget.current))
                                }
                            }
                            SectionLabel("推荐页缩略图宽度")
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(50, 75, 100).forEach { percent ->
                                    BubbleFilterChip(selected = model.recommendationMediaPercent == percent,
                                        onClick = { model.changeRecommendationMediaPercent(percent) }, label = { Text("$percent%") },
                                        modifier = Modifier.heightIn(min = LocalTouchTarget.current))
                                }
                            }
                            model.customTheme?.let { Text("正在使用自制主题：${it.name}",style=MaterialTheme.typography.bodyMedium,
                                color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=20.dp,vertical=10.dp)) }
                            BubbleOutlinedButton(onClick={ openPage("themes") },modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=LocalTouchTarget.current)) { Text("导入与管理主题") }
                            Text("浅深模式", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    BubbleFilterChip(selected = model.theme == mode, onClick = { model.setThemeMode(mode) }, label = { Text(mode.label) }, modifier = Modifier.heightIn(min = LocalTouchTarget.current))
                                }
                            }
                            Row(Modifier.fillMaxWidth().navigationBubble().clickable { colorPicker = true }.heightIn(min = LocalTouchTarget.current).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("自定义主题色", style = MaterialTheme.typography.bodyLarge)
                                    Text("#${model.accentColor} · 点按更换", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF000000 or model.accentColor.toLong(16)))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            }
                        }
                        "size" -> {
                            BubbleTextButton(onClick={ openPage("fonts") },modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=LocalTouchTarget.current)) { Text("更换字体 · ${model.appFont.label}") }
                            SizePreferences(model)
                        }
                        "media" -> {
                            SectionLabel("图片预览精度")
                            Column(Modifier.padding(horizontal=20.dp)) {
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    dev.hydrogen.tieba.data.ImageQuality.entries.forEach { quality ->
                                        BubbleFilterChip(selected=model.imageQuality==quality,onClick={ model.changeImageQuality(quality) },
                            label={ Text(quality.label) },modifier=Modifier.heightIn(min=LocalTouchTarget.current))
                                    }
                                }
                                Text(model.imageQuality.description + "。大图打开后按此精度读取。原图缺失时使用接口可用的最清晰地址。",
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PreferenceSwitch("大字号阅读", "帖子正文、评论和楼中楼字号增加", model.largeText, model::setLargeReading)
                            SectionLabel("刷帖预览图清晰度")
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                dev.hydrogen.tieba.data.ImageQuality.entries.forEach { quality ->
                                    BubbleFilterChip(selected = model.feedImageQuality == quality, onClick = { model.changeFeedImageQuality(quality) },
                                        label = { Text(quality.label) })
                                }
                            }
                            Text("列表使用接口提供的对应图片地址；高清和原画可能消耗更多流量。大图预览精度独立设置。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp))
                            PreferenceSwitch("显示装饰徽章", "控制等级旁的图片标识；保留吧内等级与吧务身份", model.showDecorativeBadges, model::changeDecorativeBadges)
                            SettingsEntry("本机内容过滤", "管理屏蔽作者、贴吧与隐藏帖子") { openPage("filters") }
                            CachePreferences()
                        }
                        "interaction" -> {
                            PreferenceSwitch("最简模式", "关闭玻璃、模糊、装饰徽章和过渡动效，动态图片显示静态画面；关闭后恢复原偏好。", model.minimalMode, model::changeMinimalMode)
                            PreferenceSwitch("液态玻璃", "导航与阅读操作栏的通透材质", model.richEffects, model::changeRichEffects)
                            PreferenceSwitch("减弱动效", "减少页面位移和按压缩放", model.reduceMotion, model::changeReduceMotion)
                            PreferenceSwitch("自动收起导航", "向下刷帖时隐藏，向上滚动时恢复", model.autoHideNavigation, model::changeAutoHideNavigation)
                        }
                        "account" -> {
                            AccountDetails(model)
                            PreferenceSwitch("旧版私信连接", "首次登录凭据缺少 HTTPS 保护；关闭后停止原生私信同步和发送，吧群与官方入口仍可使用。", model.chats.privateEnabled, model.chats::changePrivateEnabled)
                            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BubbleOutlinedButton(onClick = model::refreshAccount, enabled = !model.account.loading && model.pending == null, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text(if (model.account.loading) "正在刷新" else "刷新账号") }
                                BubbleTextButton(onClick = { if (model.account.loggedIn) logout = true else model.login() }, enabled = model.pending == null, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text(if (model.account.loggedIn) "退出登录" else "登录百度账号") }
                            }
                            model.account.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp)) }
                        }
                        "updates", "about-updates" -> UpdateSettings(model.updates)
                        "about" -> {
                            Text("氢贴吧 ${dev.hydrogen.tieba.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                            SettingsEntry("检查更新", "查看 GitHub 发布版本") { openPage("about-updates") }
                            Text("个性化推荐、关注动态及社区内容均来自百度贴吧接口。本应用转接并呈现数据，保留服务端排序；不另建推荐算法。\n\n仅供非商业学习交流使用。",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                            Text("@Bilibili Moying32", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
                }
            }
        }
    }
    // An instant branch must never retain an outgoing AnimatedContent child.
    // Recreate the animation container when the policy changes so its remembered
    // transition spec cannot carry the previous policy into the next navigation.
    val pageTransition = updateTransition(appearancePage, label = "设置页面路由")
    key(motion, instantBack) {
        if (!motion || instantBack) Box(modifier.then(backGesture)) { pageContent(appearancePage) }
        else pageTransition.AnimatedContent(modifier = modifier.then(backGesture), transitionSpec = {
            val direction = settingsDirection(initialState, targetState)
            ((fadeIn(tween(HydrogenMotion.PageDuration, easing = HydrogenMotion.EaseOut)) +
                slideInHorizontally(tween(HydrogenMotion.PageDuration, easing = HydrogenMotion.EaseOut)) { direction * (it / 20) }) togetherWith
                (fadeOut(tween(HydrogenMotion.ExitDuration)) +
                    slideOutHorizontally(tween(HydrogenMotion.ExitDuration, easing = HydrogenMotion.EaseOut)) { -direction * (it / 24) }))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(0) }))
        }) { page -> pageContent(page) }
    }
    if (colorPicker) AccentPicker(model, onDismiss = { colorPicker = false })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出当前账号？") },
        text = { Text("清除此设备中的百度登录状态，本地收藏和阅读设置会保留。") },
        confirmButton = { BubbleTextButton(onClick = { logout = false; model.logout() }) { Text("退出登录") } },
        dismissButton = { BubbleTextButton(onClick = { logout = false }) { Text("取消") } })
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(onClick = onClick)
        .heightIn(min = LocalTouchTarget.current).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SizePreferences(model: TiebaViewModel) {
    var text by rememberSaveable(model.textScale) { mutableFloatStateOf(model.textScale) }
    var interfaceSize by rememberSaveable(model.interfaceScale) { mutableFloatStateOf(model.interfaceScale) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("字体大小 · ${(text * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        GlassSlider(value = text, onValueChange = { text = (it * 100).roundToInt() / 100f }, valueRange = .5f..1.5f,
            steps = 99, label = "字体大小")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf(50,75,100,125,150).forEach { percent ->
                BubbleTextButton(onClick={ text=percent/100f },modifier=Modifier.heightIn(min=LocalTouchTarget.current)) { Text("$percent%") }
            }
        }
        Text("界面大小 · ${(interfaceSize * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        GlassSlider(value = interfaceSize, onValueChange = { interfaceSize = it },
            valueRange = .5f..1.5f, steps = 0, label = "界面大小")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(50, 75, 100, 125, 150).forEach { percent ->
                BubbleTextButton(onClick = { interfaceSize = percent / 100f }) { Text("$percent%") }
            }
        }
        Text("字体在系统字号基础上调整；界面倍率调整图标、间距和控件。点应用后生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding((14 * interfaceSize / model.interfaceScale).dp)) {
                Text("讨论值得被好好看见。", fontSize = (16 * text / model.textScale).sp)
                Text("字号预览 · 中文与 Aa 123", fontSize = (12 * text / model.textScale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { model.changeTextScale(text); model.changeInterfaceScale(interfaceSize) },
                enabled = text != model.textScale || interfaceSize != model.interfaceScale, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("应用大小") }
            BubbleTextButton(onClick = { text = 1f; interfaceSize = 1f; model.changeTextScale(1f); model.changeInterfaceScale(1f) }, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("恢复 100%") }
        }
    }
}

@Composable
private fun StylePicker(model: TiebaViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VisualStyle.entries.forEach { style ->
            val selected = model.customTheme == null && model.visualStyle == style
            Surface(shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 2.dp else .5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton,
                    onClick = { model.changeVisualStyle(style) }).semantics { contentDescription = "${style.label}主题风格" }) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    HydrogenTheme(model.theme, if (selected) model.accentColor else style.defaultAccent, style) {
                        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background,modifier=Modifier.size(54.dp)) {
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary,CircleShape))
                                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onBackground,CircleShape))
                                Box(Modifier.fillMaxWidth(.65f).height(3.dp).background(MaterialTheme.colorScheme.outlineVariant,CircleShape))
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(style.label, style = MaterialTheme.typography.labelLarge)
                        Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Outlined.Check,"当前主题",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    Text("分别记住各自的主题色，均保留液态玻璃。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
        .heightIn(min = LocalTouchTarget.current).padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AccentPicker(model: TiebaViewModel, onDismiss: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf(model.accentColor) }
    val hex = input.trim().removePrefix("#").uppercase()
    val valid = Regex("[0-9A-F]{6}").matches(hex)
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.imePadding(), title = { Text("选一个你的颜色") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选取预设，或输入任意六位十六进制色值。")
            listOf(listOf("326A54", "246BCE", "7954AB"), listOf("B64C68", "A45D20", "53616A")).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { value ->
                        val chosen = hex == value
                        Box(Modifier.size(52.dp).clip(CircleShape).selectable(chosen, role = Role.RadioButton, onClick = { input = value })
                            .semantics { contentDescription = "主题色 #$value" }.padding(6.dp)
                            .background(Color(0xFF000000 or value.toLong(16)), CircleShape)
                            .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                            if (chosen) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            AppTextField(value = input, onValueChange = { input = it.take(7) }, label = { Text("色值，例如 #246BCE") }, singleLine = true,
                isError = !valid, supportingText = { Text(if (valid) "自动适配浅色、深色与文字对比度" else "请输入六位 0–9 或 A–F，可带 #") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
            HydrogenTheme(model.theme, if (valid) hex else model.accentColor, model.visualStyle,model.customTheme,model.appFont) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("主题预览", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                        Text("讨论值得被好好看见。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                            Text("你的主题色", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }, confirmButton = { BubbleTextButton(onClick = { model.changeAccent(hex); onDismiss() }, enabled = valid) { Text("应用") } },
        dismissButton = { BubbleTextButton(onClick = onDismiss) { Text("取消") } })
}
