package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_Readings.txt`.
 *
 * Ported from the frozen Java `UnihanReadingsCodec`.
 */
public class UnihanReadingsCodec : UnihanCodec(
    fileName = "Unihan_Readings.txt",
    propertyNames = listOf(
        "kCantonese", "kDefinition", "kHangul", "kHanyuPinlu",
        "kHanyuPinyin", "kJapanese", "kJapaneseKun", "kJapaneseOn",
        "kKorean", "kMandarin", "kSMSZD2003Readings",
        "kTang", "kTGHZ2013", "kVietnamese", "kXHC1983",
    ),
)
