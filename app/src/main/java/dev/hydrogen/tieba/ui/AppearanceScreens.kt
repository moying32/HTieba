package dev.hydrogen.tieba.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.*
import dev.hydrogen.tieba.ui.theme.HydrogenTheme
import dev.hydrogen.tieba.ui.theme.appFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FontSettings(model: TiebaViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("更换字体",onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom=24.dp)) {
            Text("应用于原生页面、帖子与输入框。官方网页沿用网页字体。字体大小仍可单独调整。",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(20.dp))
            AppFont.entries.forEach { font ->
                val selected = model.appFont == font
                Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(if(selected) 2.dp else .5.dp,
                    if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=5.dp)
                        .selectable(selected,role=Role.RadioButton,onClick={ model.changeFont(font) })) {
                    Row(Modifier.padding(14.dp)) {
                        RadioButton(selected,onClick=null)
                        Column(Modifier.weight(1f).padding(start=10.dp)) {
                            Text(font.label,style=MaterialTheme.typography.titleMedium)
                            Text(font.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("讨论值得被好好看见。Aa 123",fontFamily=appFontFamily(if(font == AppFont.Theme) model.customTheme?.font ?: AppFont.System else font),
                                style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(top=10.dp))
                        }
                    }
                }
            }
            BubbleTextButton(onClick={ licenses=true },modifier=Modifier.padding(horizontal=20.dp).heightIn(min=LocalTouchTarget.current)) { Text("字体与授权") }
        }
    }
    if(licenses) {
        val text by produceState("正在读取授权…") {
            value=withContext(Dispatchers.IO) {
                listOf("noto_sans_sc","wenkai_lite","zcool_xiaowei").joinToString("\n\n") { name ->
                    context.assets.open("font_licenses/$name.txt").bufferedReader().use { "$name\n${it.readText()}" }
                }
            }
        }
        AlertDialog(onDismissRequest={ licenses=false },title={ Text("字体与授权") },text={
            SelectionContainer { Text(text,style=MaterialTheme.typography.bodySmall,modifier=Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) }
        },confirmButton={ BubbleTextButton(onClick={ licenses=false }) { Text("关闭") } })
    }
}

@Composable
fun ThemeManager(model: TiebaViewModel, onBack: () -> Unit, onGuide: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importTheme) }
    var deleting by remember { mutableStateOf<ThemePack?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("导入与管理主题",onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("选择 .h2theme 文件，先预览再应用。主题保存在本机，液态玻璃与导航手势继续沿用你的设置。",style=MaterialTheme.typography.bodyMedium)
            Button(onClick={ picker.launch(arrayOf("*/*")) },enabled=!model.themeBusy,modifier=Modifier.fillMaxWidth().heightIn(min=LocalTouchTarget.current)) { Text(if(model.themeBusy) "正在处理…" else "导入主题包") }
            BubbleOutlinedButton(onClick=onGuide,modifier=Modifier.fillMaxWidth().heightIn(min=LocalTouchTarget.current)) { Text("制作教程与 AI 提示词") }
            if(model.customThemes.isEmpty()) Text("还没有自制主题。教程中可导出示例包。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            model.customThemes.forEach { pack ->
                val selected = model.customTheme?.id == pack.id
                Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(if(selected) 2.dp else .5.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(pack.name + if(selected) " · 使用中" else "",style=MaterialTheme.typography.titleMedium)
                        Text("${pack.author} · ${pack.font.label}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(pack.description.isNotBlank()) Text(pack.description,style=MaterialTheme.typography.bodyMedium)
                        ThemeSample(pack,ThemeMode.Light)
                        ThemeSample(pack,ThemeMode.Dark)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Button(onClick={ model.chooseCustomTheme(pack) },enabled=!selected && !model.themeBusy,modifier=Modifier.heightIn(min=LocalTouchTarget.current)) { Text(if(selected) "已应用" else "应用主题") }
                            BubbleTextButton(onClick={ deleting=pack },enabled=!model.themeBusy,modifier=Modifier.heightIn(min=LocalTouchTarget.current)) { Text("删除主题",color=MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            Text("切回内置主题：返回设置，选择经典、映间 MARGIN 或 Claude 风。手动选择的字体优先于主题字体。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    model.themeImportPreview?.let { pack -> AlertDialog(onDismissRequest={ if(!model.themeBusy) model.dismissThemePreview() },title={ Text("预览 ${pack.name}") },text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("作者：${pack.author}\n字体：${pack.font.label}\n${pack.description}",style=MaterialTheme.typography.bodyMedium)
            ThemeSample(pack,ThemeMode.Light); ThemeSample(pack,ThemeMode.Dark)
            Text("配色已适配文字对比度。导入后可独立修改主题色；手动字体和液态玻璃设置保持生效。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ BubbleTextButton(onClick=model::saveImportedTheme,enabled=!model.themeBusy) { Text(if(model.themeBusy) "正在导入…" else "导入并应用") } },
        dismissButton={ BubbleTextButton(onClick=model::dismissThemePreview,enabled=!model.themeBusy) { Text("取消") } }) }
    deleting?.let { pack -> AlertDialog(onDismissRequest={ deleting=null },title={ Text("删除 ${pack.name}？") },text={ Text("从本机移除该主题。重新导入原文件可以恢复；正在使用时回到此前的内置风格。") },
        confirmButton={ BubbleTextButton(onClick={ deleting=null; model.deleteTheme(pack) }) { Text("删除") } },dismissButton={ BubbleTextButton(onClick={ deleting=null }) { Text("取消") } }) }
    ThemeError(model)
}

@Composable
private fun ThemeSample(pack: ThemePack, mode: ThemeMode) {
    HydrogenTheme(mode,pack.accent,custom=pack) {
        Surface(color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(mode == ThemeMode.Dark) "深色预览" else "浅色预览",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(pack.cornerRadius.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("讨论值得被好好看见。",style=MaterialTheme.typography.titleMedium)
                        Text("真实内容 · 清晰阅读 Aa 123",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeGuide(model: TiebaViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val guide by produceState("") { value=withContext(Dispatchers.IO) { context.assets.open("theme-guide.md").bufferedReader().use { it.readText() } } }
    val prompt by produceState("") { value=withContext(Dispatchers.IO) { context.assets.open("theme-ai-prompt.txt").bufferedReader().use { it.readText() } } }
    var copied by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(model::exportThemeExample) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeading("主题制作教程",onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Button(onClick={
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("氢贴吧主题 AI 提示词",prompt)); copied=true
            },enabled=prompt.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=LocalTouchTarget.current)) { Text(if(copied) "已复制 AI 提示词" else "复制 AI 主题提示词") }
            BubbleOutlinedButton(onClick={ exporter.launch("paper-sky.h2theme") },enabled=!model.themeBusy,modifier=Modifier.fillMaxWidth().heightIn(min=LocalTouchTarget.current)) { Text("导出示例主题包") }
            BubbleTextButton(onClick={ showPrompt=!showPrompt }) { Text(if(showPrompt) "收起完整提示词" else "查看完整提示词") }
            if(showPrompt) SelectionContainer { Text(prompt,style=MaterialTheme.typography.bodySmall) }
            guide.split("\n\n").forEach { paragraph ->
                SelectionContainer { Text(paragraph.removePrefix("# ").removePrefix("## "),style=if(paragraph.startsWith("#")) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    ThemeError(model)
}

@Composable
private fun ThemeError(model: TiebaViewModel) {
    model.themeError?.let { error -> AlertDialog(onDismissRequest=model::dismissThemeError,title={ Text("主题操作未完成") },text={ Text(error) },
        confirmButton={ BubbleTextButton(onClick=model::dismissThemeError) { Text("知道了") } }) }
}
