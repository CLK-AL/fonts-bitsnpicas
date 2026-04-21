package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `GraphemeBreakProperty.txt`.
 *
 * Ported from the frozen Java `GraphemeBreakPropertyCodec`.
 */
public class GraphemeBreakPropertyCodec : CategoryCodec(
    fileName = "GraphemeBreakProperty.txt",
    propName = "Grapheme_Cluster_Break",
    propValues = listOf(
        "Prepend", "CR", "LF", "Control", "Extend",
        "Regional_Indicator", "SpacingMark",
        "L", "V", "T", "LV", "LVT", "ZWJ",
    ),
)
