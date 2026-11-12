package com.orionxs.suivi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractKeyTest {
    private val k = "0df4d0395b7d1a876c0db9ac1a94f8b1"

    @Test fun plain() = assertEquals(k, Prefs.extractKey(k))
    @Test fun upperCase() = assertEquals(k, Prefs.extractKey(k.uppercase()))
    @Test fun inSentence() = assertEquals(k, Prefs.extractKey("Voici la clé du chargeur : $k merci"))
    @Test fun groupedAfterHexLookingWords() =
        assertEquals(k, Prefs.extractKey("Voici la cle : 0df4d039 5b7d1a87 6c0db9ac 1a94f8b1"))
    @Test fun groupedWithColons() =
        assertEquals(k, Prefs.extractKey("cle:0df4d039:5b7d1a87:6c0db9ac:1a94f8b1"))
    @Test fun multiline() = assertEquals(k, Prefs.extractKey("Bonjour\nla clé :\n$k\nmerci"))
    @Test fun tooShort() = assertNull(Prefs.extractKey("0df4d0395b7d1a876c0db9ac1a94f8b"))
    @Test fun nothing() = assertNull(Prefs.extractKey("bonjour, ça va ?"))
}
