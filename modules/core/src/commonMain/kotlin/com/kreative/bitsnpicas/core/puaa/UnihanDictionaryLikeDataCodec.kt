package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_DictionaryLikeData.txt`.
 *
 * Ported from the frozen Java `UnihanDictionaryLikeDataCodec`.
 */
public class UnihanDictionaryLikeDataCodec : UnihanCodec(
    fileName = "Unihan_DictionaryLikeData.txt",
    propertyNames = listOf(
        "kAlternateTotalStrokes", "kCangjie", "kCheungBauer",
        "kFenn", "kFourCornerCode", "kFrequency", "kGradeLevel",
        "kHDZRadBreak", "kHKGlyph", "kMojiJoho", "kPhonetic",
        "kStrange", "kUnihanCore2020",
    ),
)
