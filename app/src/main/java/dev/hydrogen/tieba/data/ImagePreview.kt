package dev.hydrogen.tieba.data

import java.net.URI

/** Preview quality limits decoded pixels, not the quality of the downloaded thumbnail. */
internal fun ImageSource.previewUrl(quality: ImageQuality): String = when (quality) {
    ImageQuality.Low, ImageQuality.Medium -> listOf(medium, high, original, preview)
        .firstOrNull { it.isNotBlank() && !isCropped(it) } ?: url(quality)
    ImageQuality.High -> original.ifBlank { url(quality) }
    ImageQuality.Original -> url(quality)
}

internal fun ImageSource.needsPreviewDetails(quality: ImageQuality): Boolean = when (quality) {
    ImageQuality.Low, ImageQuality.Medium -> (previewUrl(quality) == preview && original.isBlank()) || isCropped(previewUrl(quality))
    ImageQuality.High, ImageQuality.Original -> original.isBlank() ||
        (imageId(original) != null && runCatching { URI(original).path.contains("/sign=") }.getOrDefault(false))
}

private fun isCropped(url: String): Boolean = imageId(url) != null && runCatching {
    val path = URI(url).path.orEmpty()
    path.contains("/whfpf=") || path.contains("/wh=")
}.getOrDefault(false)

private fun ImageSource.addresses() = listOf(preview, medium, high, original).filter(String::isNotBlank)

/** Compare image identity, never transplant query signatures or invent original URLs. */
private fun imageId(url: String): String? = runCatching {
    val uri = URI(url)
    val host = uri.host.orEmpty().lowercase()
    if (host !in setOf("imgsrc.baidu.com", "tiebapic.baidu.com", "imgsa.baidu.com") &&
        !host.endsWith(".hiphotos.baidu.com")) return null
    uri.path.orEmpty().substringAfterLast('/').lowercase().takeIf {
        Regex("[a-f0-9]{32,64}\\.(?:jpe?g|png|gif|webp|avif)").matches(it)
    }
}.getOrNull()

internal fun ImageGallery.withDetailImages(detail: ThreadPage): ImageGallery {
    require(threadId.isNotBlank() && threadId == detail.thread.id) { "图片所属帖子不匹配" }
    val originals = detail.posts.filter { it.floor == 1 }.flatMap { post ->
        post.content.mapNotNull { it.imageSource }
    } + detail.thread.galleryImages()
    return copy(sources = sources.map { source ->
        val addresses = source.addresses()
        val ids = addresses.mapNotNull(::imageId).toSet()
        val matches = originals.filter { candidate -> candidate.addresses().any { address ->
            address in addresses || imageId(address)?.let(ids::contains) == true
        } }
        source.copy(
            medium = matches.firstOrNull { it.medium.isNotBlank() }?.medium ?: source.medium,
            high = matches.firstOrNull { it.high.isNotBlank() }?.high ?: source.high,
            original = matches.firstOrNull { it.original.isNotBlank() }?.original ?: source.original,
        )
    })
}
