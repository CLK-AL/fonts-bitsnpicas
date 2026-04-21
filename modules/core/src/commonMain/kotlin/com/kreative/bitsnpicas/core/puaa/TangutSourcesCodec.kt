package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `TangutSources.txt`.
 *
 * Ported from the frozen Java `TangutSourcesCodec`.
 */
public class TangutSourcesCodec : UnihanCodec(
    fileName = "TangutSources.txt",
    propertyNames = listOf("kTGT_MergedSrc", "kRSTUnicode"),
)
