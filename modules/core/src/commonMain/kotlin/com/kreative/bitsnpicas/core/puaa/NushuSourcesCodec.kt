package com.kreative.bitsnpicas.core.puaa

/**
 * Codec for `NushuSources.txt`.
 *
 * Ported from the frozen Java `NushuSourcesCodec`.
 */
public class NushuSourcesCodec : UnihanCodec(
    fileName = "NushuSources.txt",
    propertyNames = listOf("kSrc_NushuDuben", "kReading"),
)
