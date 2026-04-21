package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `emoji-data.txt`.
 *
 * Ported from the frozen Java `EmojiDataCodec` (extends `AbstractPropListCodec`).
 */
public class EmojiDataCodec : PropListCodec(
    fileName = "emoji-data.txt",
    propertyNames = listOf(
        "Emoji", "Emoji_Presentation", "Emoji_Modifier",
        "Emoji_Modifier_Base", "Emoji_Component", "Extended_Pictographic",
    ),
)
