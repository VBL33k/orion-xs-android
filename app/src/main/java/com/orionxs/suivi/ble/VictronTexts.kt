package com.orionxs.suivi.ble

/** Libellés en français des états / erreurs / raisons d'arrêt Victron. */
object VictronTexts {
    fun state(s: Int?): String = when (s) {
        null -> "Inconnu"
        0 -> "Éteint"
        1 -> "Basse consommation"
        2 -> "Défaut"
        3 -> "En charge (bulk)"
        4 -> "Absorption"
        5 -> "Floating (entretien)"
        6 -> "Stockage"
        7 -> "Égalisation"
        9 -> "Onduleur"
        11 -> "Alimentation"
        245 -> "Démarrage"
        246 -> "Absorption répétée"
        247 -> "Reconditionnement"
        248 -> "BatterySafe"
        249 -> "Actif"
        252 -> "Contrôle externe"
        else -> "État $s"
    }

    /** true si le chargeur fournit du courant. */
    fun isCharging(s: Int?): Boolean = s != null && s in setOf(3, 4, 5, 6, 7, 11, 246, 247, 248, 249, 252)

    fun error(e: Int?): String? = when (e) {
        null, 0 -> null
        1 -> "Température batterie trop haute"
        2 -> "Tension batterie trop haute"
        14 -> "Température batterie trop basse"
        17 -> "Chargeur trop chaud"
        18 -> "Surintensité"
        20 -> "Durée de charge dépassée"
        26 -> "Surchauffe"
        27 -> "Court-circuit"
        28 -> "Problème de convertisseur"
        33 -> "Tension d'entrée trop haute"
        34 -> "Courant d'entrée trop haut"
        35 -> "Puissance d'entrée trop haute"
        38 -> "Coupure : tension d'entrée"
        39 -> "Coupure : courant d'entrée"
        else -> "Erreur n° $e"
    }

    fun offReason(r: Long): String? {
        if (r == 0L) return null
        val parts = mutableListOf<String>()
        if (r and 0x1L != 0L) parts += "pas de tension d'entrée"
        if (r and 0x2L != 0L) parts += "éteint par l'interrupteur"
        if (r and 0x4L != 0L) parts += "éteint dans les réglages"
        if (r and 0x8L != 0L) parts += "commande à distance"
        if (r and 0x10L != 0L) parts += "protection active"
        if (r and 0x20L != 0L) parts += "crédit épuisé"
        if (r and 0x40L != 0L) parts += "BMS"
        if (r and 0x80L != 0L) parts += "moteur arrêté détecté"
        if (r and 0x100L != 0L) parts += "analyse de la tension d'entrée"
        if (parts.isEmpty()) parts += "code 0x" + r.toString(16)
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    fun recordType(t: Int): String = when (t) {
        0x01 -> "Régulateur solaire"
        0x02 -> "Contrôleur de batterie"
        0x03 -> "Onduleur"
        0x04 -> "Convertisseur DC-DC"
        0x05 -> "Batterie lithium"
        0x08 -> "Chargeur AC"
        0x09 -> "Smart BatteryProtect"
        0x0D -> "Compteur DC"
        0x0F -> "Orion XS"
        else -> "Type 0x" + t.toString(16)
    }
}
