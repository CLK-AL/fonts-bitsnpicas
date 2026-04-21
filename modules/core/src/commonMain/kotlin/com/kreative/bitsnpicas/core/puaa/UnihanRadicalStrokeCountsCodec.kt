package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_RadicalStrokeCounts.txt`.
 *
 * Ported from the frozen Java `UnihanRadicalStrokeCountsCodec`.
 */
public class UnihanRadicalStrokeCountsCodec : UnihanCodec(
    fileName = "Unihan_RadicalStrokeCounts.txt",
    propertyNames = listOf(
        "kRSAdobe_Japan1_6", "kRSJapanese", "kRSKangXi",
        "kRSKanWa", "kRSKorean",
    ),
)
