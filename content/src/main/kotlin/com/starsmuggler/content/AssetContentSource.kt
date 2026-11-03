package com.starsmuggler.content

import android.content.res.AssetManager

/** Reads the content files from the app assets. */
class AssetContentSource(private val assets: AssetManager) : ContentSource {
    override fun read(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
