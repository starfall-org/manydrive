package com.starfall.gsadrive.shared.i18n

import com.starfall.gsadrive.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Resolves default resources on the JVM, where Android Resources is unavailable. */
internal fun installTestStrings() {
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/values/strings.xml")).getElementsByTagName("string")
    val strings = (0 until nodes.length).associate { index ->
        val node = nodes.item(index)
        val name = node.attributes.getNamedItem("name").nodeValue
        R.string::class.java.getField(name).getInt(null) to node.textContent
            .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
    }
    testStringResolver = { id, args ->
        val text = strings.getValue(id)
        if (args.isEmpty()) text else String.format(Locale.ENGLISH, text, *args)
    }
}

internal fun clearTestStrings() { testStringResolver = null }
