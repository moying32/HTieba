package dev.hydrogen.tieba.ui

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

internal fun photoPickerRequest(maxItems: Int? = null): PickVisualMediaRequest =
    PickVisualMediaRequest.Builder().setMediaType(PickVisualMedia.ImageOnly).apply {
        if (maxItems != null) {
            setMaxItems(maxItems)
            setOrderedSelection(true)
        }
    }.build()
