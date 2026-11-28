package dev.hydrogen.tieba.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import kotlin.math.abs
import kotlin.math.floor

@Composable
internal fun ThemeIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    pixelEmphasis: Boolean = false,
) {
    val glyph = if (LocalVisualStyle.current == VisualStyle.Pixel) {
        pixelIcons[imageVector.name.substringAfterLast('.').replace("AutoMirrored", "").trimStart('.')]
    } else null
    if (glyph == null) {
        Icon(imageVector, contentDescription, modifier, tint)
        return
    }
    val mirror = imageVector.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Canvas(modifier.then(semantics).size(24.dp)) {
        val unit = floor(size.minDimension / 16f).coerceAtLeast(1f)
        val origin = Offset(floor((size.width - 16 * unit) / 2f), floor((size.height - 16 * unit) / 2f))
        val cellSize = unit + if (pixelEmphasis && unit >= 2f) 1f else 0f
        for (index in glyph) {
            val column = index % 16
            val x = if (mirror) 15 - column else column
            drawRect(tint, origin + Offset(x * unit, index / 16 * unit), Size(cellSize, cellSize))
        }
    }
}

/** Each path is drawn on a 16×16 grid, using hexadecimal x/y pairs. */
private fun glyph(vararg paths: String): IntArray {
    val cells = linkedSetOf<Int>()
    for (path in paths) {
        val points = path.split(' ').map { it[0].digitToInt(16) to it[1].digitToInt(16) }
        cells += points.first().let { (x, y) -> y * 16 + x }
        for ((start, end) in points.zipWithNext()) {
            var (x, y) = start
            val (targetX, targetY) = end
            val dx = abs(targetX - x)
            val dy = -abs(targetY - y)
            val stepX = if (x < targetX) 1 else -1
            val stepY = if (y < targetY) 1 else -1
            var error = dx + dy
            while (x != targetX || y != targetY) {
                val twice = error * 2
                if (twice >= dy) { error += dy; x += stepX }
                if (twice <= dx) { error += dx; y += stepY }
                cells += y * 16 + x
            }
        }
    }
    return cells.toIntArray()
}

private val chat = glyph("32 c2 e4 eb db 6b 3e 3b 2b 24 32", "56 a6", "58 88")
private val left = glyph("82 27 8c", "37 d7", "38 d8")
private val right = glyph("72 d7 7c", "27 c7", "28 c8")
private val picture = glyph("32 d2 dd 3d 32", "3a 66 a9 b7 da", "a5 b5 a6 b6")
private val heart = glyph("73 54 33 25 28 7d cd e8 e5 d3 b3 94 73", "6c 8e dc")
private val pixelIcons = mapOf(
    "Home" to glyph("18 81 e8", "28 81 d8", "38 3d 6d 69 99 9d cd c8", "59 5c", "ad bd"),
    "Explore" to glyph("52 b2 e5 ea bd 5d 2a 25 52", "75 b4 ab 4c 75", "86 69", "96 79"),
    "Add" to glyph("82 8d", "72 7d", "27 d7", "28 d8"),
    "ChatBubbleOutline" to chat,
    "ChatBubble" to chat,
    "PersonOutline" to glyph("61 a1 c3 c6 a8 68 46 43 61", "3b 5a ba db dd 3d 3b"),
    "Person" to glyph("61 a1 c3 c6 a8 68 46 43 61", "54 b4", "55 b5", "3b 5a ba db dd 3d 3b", "4c cc"),
    "Search" to glyph("42 92 b4 b9 9b 4b 29 24 42", "a9 ee", "a8 ed"),
    "Refresh" to glyph("28 26 44 a4 d7", "d3 d7 97", "d9 db bd 5d 2a"),
    "Replay" to glyph("d7 d5 b3 53 27", "22 27 77", "2a 5d bd da"),
    "Settings" to glyph("61 a1 a3 c4 e4 e8 c8 ba bc ad 6d 5c 5a 38 18 14 34 63 61", "66 a6 a9 69 66"),
    "Close" to glyph("33 cc", "34 cd", "c3 3c", "b3 3b"),
    "Check" to glyph("28 6c d4", "29 6d d5"),
    "ArrowBack" to left,
    "ArrowForward" to right,
    "ChevronRight" to glyph("53 a8 5d", "63 b8 6d"),
    "KeyboardArrowRight" to glyph("53 a8 5d", "63 b8 6d"),
    "KeyboardArrowLeft" to glyph("a3 58 ad", "93 48 9d"),
    "KeyboardArrowDown" to glyph("35 8a d5", "36 8b d6"),
    "ExpandMore" to glyph("35 8a d5", "36 8b d6"),
    "KeyboardArrowUp" to glyph("3a 85 da", "3b 86 db"),
    "ExpandLess" to glyph("3a 85 da", "3b 86 db"),
    "NorthWest" to glyph("33 93", "33 39", "33 cc", "34 cd"),
    "MoreHoriz" to glyph("37 47 48 38 37", "77 87 88 78 77", "b7 c7 c8 b8 b7"),
    "FavoriteBorder" to heart,
    "Favorite" to glyph("32 62 84 a2 d2 e4 e8 8e 2a 18 14 32", "26 e6", "27 e7", "28 d8", "39 c9", "4a ba", "5b ab", "6c 9c", "7d 8d"),
    "ThumbUp" to glyph("2d 29 49 72 92 95 d5 e7 cd 2d", "4a 4d", "6b bb"),
    "BookmarkBorder" to glyph("42 c2 cd 88 4d 42"),
    "Bookmark" to glyph("42 c2 cd 88 4d 42", "53 b3", "54 b4", "55 b5", "56 b6", "57 b7", "58 b8", "59 69", "a9 b9"),
    "StarOutline" to glyph("81 96 e6 a9 bd 8b 4d 69 26 76 81"),
    "Share" to glyph("b2 d2 d4 b4 b2", "27 47 49 29 27", "bb db dd bd bb", "47 b3", "49 bc"),
    "Article" to glyph("32 b2 d4 dd 3d 32", "a2 a5 d5", "57 b7", "59 b9", "5b 9b"),
    "Edit" to glyph("3b b3 c2 e4 d5 5d 3d 3b", "a4 c6"),
    "Forum" to glyph("22 b2 b8 68 3b 38 28 22", "44 94", "46 86", "d5 e5 eb cb cc 8b 6b 69"),
    "GroupAdd" to glyph("41 81 a3 a6 88 48 26 23 41", "1d 1b 3a 9a bb bd 1d", "ba ea", "d8 dc"),
    "History" to glyph("34 52 b2 e5 ea bd 5d 3b", "31 35 75", "84 87 b9"),
    "Image" to picture,
    "ImageNotSupported" to glyph("32 d2 d8", "dd 3d 35", "22 dd", "23 de"),
    "AddPhotoAlternate" to glyph("32 82", "32 3d dd d8", "3a 66 a9 b7 da", "a4 e4", "c2 c6"),
    "Wallpaper" to glyph("22 52", "22 25", "a2 d2 d5", "2a 2d 5d", "ad dd da", "4a 76 a8 ba"),
    "Download" to glyph("83 8a", "73 7a", "56 8a b7", "4d cd"),
    "DeleteOutline" to glyph("25 d5", "62 92 94 64 62", "44 4d bd b4", "66 6b", "96 9b"),
    "ContentCopy" to glyph("32 a2", "32 3a", "64 d4 dd 6d 64"),
    "Info" to glyph("52 b2 e5 ea bd 5d 2a 25 52", "85", "87 8b", "77 7b"),
    "AlternateEmail" to glyph("ee 5e 2b 24 52 b2 e5 e9 c9 a6 66 4a 6c 9c ac a7"),
    "Circle" to glyph("66 96 99 69 66", "67 97", "68 98"),
    "PushPin" to glyph("53 b3", "64 a4 a7 ba 4a 67 64", "7a 7e"),
    "LocalFireDepartment" to glyph("81 a4 a8 c5 e8 eb bd 5d 2b 28 66 81", "8c 6a 88 aa 8c"),
    "Fullscreen" to glyph("26 23 53", "a3 d3 d6", "29 2c 5c", "ac dc d9"),
    "FullscreenExit" to glyph("53 56 26", "a3 a6 d6", "29 59 5c", "d9 a9 ac"),
    "PlayArrow" to glyph("42 4d e8 42", "54 75", "55 96", "56 b7", "57 d8", "58 b9", "59 9a", "5a 7b"),
    "Pause" to glyph("43 4c", "53 5c", "a3 ac", "b3 bc"),
    "FastForward" to glyph("33 3c 87 33", "93 9c e7 93", "45 67 49", "a5 c7 a9"),
    "Send" to glyph("22 e7 2d 27 e7", "22 27", "37 3b"),
    "VolumeOff" to glyph("32 dc", "35 55 83 88", "39 59 8c 8a", "35 39"),
    "VolumeUp" to glyph("35 55 83 8c 59 39 35", "a5 b6 b9 aa", "c3 e5 ea cc"),
)
