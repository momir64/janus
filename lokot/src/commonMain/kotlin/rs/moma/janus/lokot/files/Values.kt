package rs.moma.janus.lokot.files

/**
 * The vault holds its values as characters, since a String cannot be overwritten. These two are for
 * the places that deal in text anyway: the CLI, which puts the whole document in an editor, and the
 * checks, which compare documents. The library's own path never calls either.
 */
internal fun Map<String, CharArray>.asText(): Map<String, String> = mapValues { it.value.concatToString() }

internal fun Map<String, String>.asChars(): Map<String, CharArray> = mapValues { it.value.toCharArray() }
