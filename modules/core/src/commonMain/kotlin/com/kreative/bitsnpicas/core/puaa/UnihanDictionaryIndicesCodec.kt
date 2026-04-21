package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `Unihan_DictionaryIndices.txt`.
 *
 * Ported from the frozen Java `UnihanDictionaryIndicesCodec`.
 */
public class UnihanDictionaryIndicesCodec : UnihanCodec(
    fileName = "Unihan_DictionaryIndices.txt",
    propertyNames = listOf(
        "kCheungBauerIndex", "kCihaiT", "kCowles", "kDaeJaweon",
        "kFennIndex", "kGSR", "kHanYu", "kIRGDaeJaweon",
        "kIRGDaiKanwaZiten", "kIRGHanyuDaZidian", "kIRGKangXi",
        "kKangXi", "kKarlgren", "kLau", "kMatthews",
        "kMeyerWempe", "kMorohashi", "kNelson", "kSBGY",
        "kSMSZD2003Index",
    ),
)
