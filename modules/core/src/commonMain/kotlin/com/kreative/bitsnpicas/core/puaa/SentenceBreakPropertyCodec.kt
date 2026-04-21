package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `SentenceBreakProperty.txt`.
 *
 * Ported from the frozen Java `SentenceBreakPropertyCodec`.
 */
public class SentenceBreakPropertyCodec : CategoryCodec(
    fileName = "SentenceBreakProperty.txt",
    propName = "Sentence_Break",
    propValues = listOf(
        "CR", "LF", "Extend", "Sep", "Format", "Sp",
        "Lower", "Upper", "OLetter", "Numeric",
        "ATerm", "STerm", "Close", "SContinue",
    ),
)
