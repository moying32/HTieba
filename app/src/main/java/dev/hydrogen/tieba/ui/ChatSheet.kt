package dev.hydrogen.tieba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Observe IME in the sheet's own window so back hides it before dismissing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var keyboardVisible by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !keyboardVisible)) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val visible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        SideEffect { keyboardVisible = visible }
        BackHandler(visible) { keyboard?.hide(); focus.clearFocus() }
        content()
    }
}
