package dev.hydrogen.tieba.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { if (input.isNotBlank()) { keyboard?.hide(); onSearch(input) } }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("去一个吧，或打开帖子") },
        text = {
            OutlinedTextField(input, onValueChange = { input = it }, placeholder = { Text("吧名 / 帖子链接") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }), shape = RoundedCornerShape(14.dp))
        },
        confirmButton = { BubbleTextButton(onClick = submit, enabled = input.isNotBlank()) { Text("前往") } },
        dismissButton = { BubbleTextButton(onClick = onDismiss) { Text("取消") } })
}

