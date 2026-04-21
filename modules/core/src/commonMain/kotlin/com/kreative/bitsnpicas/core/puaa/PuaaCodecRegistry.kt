package com.kreative.bitsnpicas.core.puaa

/**
 * Registry that maps filename patterns to [PuaaCodec] instances.
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.PuaaCodecRegistry`.
 */
public class PuaaCodecRegistry private constructor() {

    private val codecs = sortedMapOf<String, PuaaCodec>()

    init {
        // Previously ported codecs
        addCodec(BlocksCodec())
        addCodec(PropListCodec())
        addCodec(ScriptsCodec())
        addCodec(UnicodeDataCodec())
        // AbstractStringCodec derivatives
        addCodec(EastAsianWidthCodec())
        addCodec(LineBreakCodec())
        addCodec(VerticalOrientationCodec())
        // AbstractCategoryCodec derivatives
        addCodec(GraphemeBreakPropertyCodec())
        addCodec(SentenceBreakPropertyCodec())
        addCodec(WordBreakPropertyCodec())
        addCodec(IndicPositionalCategoryCodec())
        addCodec(IndicSyllabicCategoryCodec())
        // AbstractPropListCodec derivatives
        addCodec(EmojiDataCodec())
        // Standalone codecs
        addCodec(ArabicShapingCodec())
        addCodec(BidiBracketsCodec())
        addCodec(BidiMirroringCodec())
        addCodec(CompositionExclusionsCodec())
        addCodec(DerivedAgeCodec())
        addCodec(EquivalentUnifiedIdeographCodec())
        addCodec(HangulSyllableTypeCodec())
        addCodec(JamoCodec())
        addCodec(NameAliasesCodec())
        addCodec(ScriptExtensionsCodec())
        addCodec(SpecialCasingCodec())
        // AbstractUnihanCodec derivatives
        addCodec(NushuSourcesCodec())
        addCodec(TangutSourcesCodec())
        addCodec(UnihanDictionaryIndicesCodec())
        addCodec(UnihanDictionaryLikeDataCodec())
        addCodec(UnihanIRGSourcesCodec())
        addCodec(UnihanNumericValuesCodec())
        addCodec(UnihanOtherMappingsCodec())
        addCodec(UnihanRadicalStrokeCountsCodec())
        addCodec(UnihanReadingsCodec())
        addCodec(UnihanVariantsCodec())
    }

    public fun addCodec(codec: PuaaCodec) {
        codecs[codec.fileName.lowercase()] = codec
    }

    /**
     * Look up a codec by filename (case-insensitive).
     */
    public fun getCodec(fileName: String): PuaaCodec? {
        return codecs[fileName.lowercase()]
    }

    /**
     * All registered codecs, sorted by filename.
     */
    public fun getCodecs(): Collection<PuaaCodec> = codecs.values

    public companion object {
        /** Singleton instance with the default set of codecs. */
        public val instance: PuaaCodecRegistry = PuaaCodecRegistry()
    }
}
