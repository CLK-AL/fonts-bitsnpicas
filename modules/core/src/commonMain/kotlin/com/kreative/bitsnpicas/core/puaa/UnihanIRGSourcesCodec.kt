package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_IRGSources.txt`.
 *
 * Ported from the frozen Java `UnihanIRGSourcesCodec`.
 */
public class UnihanIRGSourcesCodec : UnihanCodec(
    fileName = "Unihan_IRGSources.txt",
    propertyNames = listOf(
        "kCompatibilityVariant", "kIICore",
        "kIRG_GSource", "kIRG_HSource", "kIRG_JSource",
        "kIRG_KPSource", "kIRG_KSource", "kIRG_MSource",
        "kIRG_SSource", "kIRG_TSource", "kIRG_UKSource",
        "kIRG_USource", "kIRG_VSource",
        "kRSUnicode", "kTotalStrokes",
    ),
)
