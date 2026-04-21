package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `IndicPositionalCategory.txt`.
 *
 * Ported from the frozen Java `IndicPositionalCategoryCodec`.
 */
public class IndicPositionalCategoryCodec : CategoryCodec(
    fileName = "IndicPositionalCategory.txt",
    propName = "Indic_Positional_Category",
    propValues = listOf(
        "Right", "Left", "Visual_Order_Left", "Left_And_Right",
        "Top", "Bottom", "Top_And_Bottom",
        "Top_And_Right", "Top_And_Left", "Top_And_Left_And_Right",
        "Bottom_And_Right", "Bottom_And_Left",
        "Top_And_Bottom_And_Right", "Top_And_Bottom_And_Left",
        "Overstruck",
    ),
)
