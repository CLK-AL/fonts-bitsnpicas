package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_NumericValues.txt`.
 *
 * Ported from the frozen Java `UnihanNumericValuesCodec`.
 */
public class UnihanNumericValuesCodec : UnihanCodec(
    fileName = "Unihan_NumericValues.txt",
    propertyNames = listOf(
        "kAccountingNumeric", "kOtherNumeric", "kPrimaryNumeric",
        "kVietnameseNumeric", "kZhuangNumeric",
    ),
)
