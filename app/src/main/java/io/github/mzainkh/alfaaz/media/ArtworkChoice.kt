package io.github.mzainkh.alfaaz.media

import android.media.MediaMetadata

/** Where the player's art for a track can be read. */
sealed interface ArtSource<out B> {
    data class Image<B>(val image: B) : ArtSource<B>
    data class Link(val uri: String) : ArtSource<Nothing>
}

private val IMAGE_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ALBUM_ART,
    MediaMetadata.METADATA_KEY_ART,
    MediaMetadata.METADATA_KEY_DISPLAY_ICON,
)
private val LINK_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
    MediaMetadata.METADATA_KEY_ART_URI,
    MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
)

/** Plain http is blocked by Android; player-specific schemes such as `spotify:image:` cannot be opened. */
private val LINK_SCHEMES = setOf("https", "content", "android.resource")

/** The image the player sent, else a link to one that can be opened. */
fun <B : Any> chooseArt(image: (String) -> B?, text: (String) -> String?): ArtSource<B>? {
    IMAGE_KEYS.firstNotNullOfOrNull(image)?.let { return ArtSource.Image(it) }
    return LINK_KEYS.firstNotNullOfOrNull { key ->
        text(key)?.trim()?.takeIf { it.substringBefore(':', "").lowercase() in LINK_SCHEMES }
    }?.let { ArtSource.Link(it) }
}
