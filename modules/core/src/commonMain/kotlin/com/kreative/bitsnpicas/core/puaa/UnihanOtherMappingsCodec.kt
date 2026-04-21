package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_OtherMappings.txt`.
 *
 * Ported from the frozen Java `UnihanOtherMappingsCodec`.
 */
public class UnihanOtherMappingsCodec : UnihanCodec(
    fileName = "Unihan_OtherMappings.txt",
    propertyNames = listOf(
        "kBigFive", "kCCCII", "kCNS1986", "kCNS1992", "kEACC",
        "kGB0", "kGB1", "kGB3", "kGB5", "kGB7", "kGB8",
        "kHKSCS", "kIBMJapan", "kJa", "kJinmeiyoKanji",
        "kJis0", "kJis1", "kJIS0213", "kJoyoKanji",
        "kKPS0", "kKPS1", "kKSC0", "kKSC1",
        "kKoreanEducationHanja", "kKoreanName",
        "kMainlandTelegraph", "kPseudoGB1", "kTaiwanTelegraph",
        "kTGH", "kXerox",
    ),
)
