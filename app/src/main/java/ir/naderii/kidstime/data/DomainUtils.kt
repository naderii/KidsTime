package ir.naderii.kidstime.data

private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

/** Returns a normalized domain (e.g. "example.com") or null if invalid. */
fun normalizeDomain(input: String): String? {
    var s = input.trim().lowercase()
        .removePrefix("https://").removePrefix("http://")
        .substringBefore("/").substringBefore("?").substringBefore("#")
        .substringAfter("@")
        .substringBefore(":")
        .trim().trimEnd('.')
        .removePrefix("www.")
    if (s.isEmpty() || s.length > 253) return null
    val labels = s.split(".")
    if (labels.size < 2) return null
    if (!labels.all { LABEL.matches(it) }) return null
    if (labels.last().all { it.isDigit() }) return null // IP address / numeric TLD
    return s
}
