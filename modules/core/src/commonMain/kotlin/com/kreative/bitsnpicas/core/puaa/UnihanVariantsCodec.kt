package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_Variants.txt`.
 *
 * Ported from the frozen Java `UnihanVariantsCodec`.
 */
public class UnihanVariantsCodec : UnihanCodec(
    fileName = "Unihan_Variants.txt",
    propertyNames = listOf(
        "kSemanticVariant", "kSimplifiedVariant",
        "kSpecializedSemanticVariant", "kSpoofingVariant",
        "kTraditionalVariant", "kZVariant",
    ),
)
