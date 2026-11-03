package com.starsmuggler.content

/**
 * Language of the long game texts (rules r### and events e###), kept as assets in one folder
 * per language: `text/es/`. The MVP ships Spanish only (PRD section 10); adding a language is
 * adding its folder and its code to [supported] (SMUG-80).
 */
object TextLanguage {
    /** Used when the phone asks for a language the game does not have. */
    const val DEFAULT = "es"

    /** Languages with a full set of texts, default first. */
    val supported: List<String> = listOf(DEFAULT)

    /** The game language for a BCP 47 or Android language tag, such as `es-AR` or `en_US`. */
    fun resolve(languageTag: String): String {
        val language = languageTag.substringBefore('-').substringBefore('_').lowercase()
        return if (language in supported) language else DEFAULT
    }

    /** Asset folder of the long texts in [language]. */
    fun assetDir(language: String): String = "text/${resolve(language)}"
}
