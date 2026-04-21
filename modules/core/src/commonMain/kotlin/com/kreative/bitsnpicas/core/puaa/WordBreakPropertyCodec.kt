package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `WordBreakProperty.txt`.
 *
 * Ported from the frozen Java `WordBreakPropertyCodec`.
 */
public class WordBreakPropertyCodec : CategoryCodec(
    fileName = "WordBreakProperty.txt",
    propName = "Word_Break",
    propValues = listOf(
        "Double_Quote", "Single_Quote", "Hebrew_Letter",
        "CR", "LF", "Newline", "Extend", "Regional_Indicator",
        "Format", "Katakana", "ALetter", "MidLetter",
        "MidNum", "MidNumLet", "Numeric", "ExtendNumLet",
        "ZWJ", "WSegSpace",
    ),
)
