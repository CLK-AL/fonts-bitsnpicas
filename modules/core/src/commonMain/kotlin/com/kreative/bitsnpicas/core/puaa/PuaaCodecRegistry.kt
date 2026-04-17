package com.kreative.bitsnpicas.core.puaa

/**
 * Registry that maps filename patterns to [PuaaCodec] instances.
 *
 * Ported from the frozen Java `com.kreative.bitsnpicas.puaa.PuaaCodecRegistry`.
 *
 * Only the most common codecs are registered here as a proof of concept;
 * exotic ones (Unihan, NameAliases, SpecialCasing, etc.) can be added later.
 */
public class PuaaCodecRegistry private constructor() {

    private val codecs = sortedMapOf<String, PuaaCodec>()

    init {
        addCodec(BlocksCodec())
        addCodec(PropListCodec())
        addCodec(ScriptsCodec())
        addCodec(UnicodeDataCodec())
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
