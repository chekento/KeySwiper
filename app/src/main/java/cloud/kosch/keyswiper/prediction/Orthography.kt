package cloud.kosch.keyswiper.prediction

/** Conservative display spelling. Ambiguous nouns are handled separately from safe nouns. */
object Orthography {
    val germanNouns: Set<String> = """
        Arbeit Arbeiten Antwort Antworten App Apps Auswahl Beispiel Beispiele Benutzer Build Code Daten
        Datei Dateien Detail Details Download Downloads Eingabe Eingaben Einstellung Einstellungen
        Ergebnis Ergebnisse Fehler Feature Features Funktion Funktionen Idee Ideen Information Informationen
        Intelligenz Kontext Korrektur Korrekturen Leiste Lösung Lösungen Modell Modelle Nachricht Nachrichten
        Option Optionen Profil Profile Projekt Projekte Quelle Quellen Satz Sätze Sprache Sprachen
        Tastatur Tastaturen Taste Tasten Text Texte Übersetzung Übersetzungen Version Versionen
        Vorschlag Vorschläge Wort Wörter Verbesserung Verbesserungen Vorschläge Vervollständigung
        Großschreibung Kleinschreibung Umlaut Umlaute Buchstabe Buchstaben Zeichen Sonderzeichen
        Punkt Komma Doppelpunkt Semikolon Fragezeichen Ausrufezeichen Absatz Absätze Zwischenablage
        Tag Tage Zeit Zeiten Abend Mittag Nacht Wochenende Montag Dienstag Mittwoch Donnerstag Freitag Samstag Sonntag
        Woche Wochen Monat Monate Jahr Jahre Uhr Minute Minuten Sekunde Sekunden Stunde Stunden
        Termin Termine Besuch Pause Urlaub Ferien Reise Reisen Haus Häuser Wohnung Wohnungen Zimmer
        Küche Garten Tür Türen Fenster Auto Autos Bus Bahn Zug Züge Fahrrad Flugzeug Straße Straßen
        Stadt Städte Land Länder Adresse Ort Schule Universität Büro Supermarkt Geld Preis Preise
        Frühstück Kaffee Tee Wasser Milch Brot Butter Käse Obst Gemüse Apfel Äpfel Banane Pizza Nudeln
        Reis Salat Suppe Restaurant Tisch Familie Freund Freunde Freundin Eltern Mutter Vater Kind Kinder
        Bruder Schwester Geburtstag Glückwunsch Glückwünsche Liebe Entschuldigung Frage Fragen Nummer
        Telefon Handy Bild Bilder Foto Fotos Video Videos Musik Film Buch Bücher Wetter Regen Sonne
        Schnee Wind Grund Gründe Größe Größen Gefühl Gefühle Möglichkeit Möglichkeiten Problem Probleme
        Männer Frau Frauen Mädchen Öl Ärger Übung Übungen Öffentlichkeit Behörden Behörde Möbel Lösung
        Unterstützung Gespräch Gespräche Rückmeldung Rückmeldungen Nachricht Terminplanung Besprechung
        Unterlage Unterlagen Dokument Dokumente Anhang Anhänge Bericht Berichte Aufgabe Aufgaben
        Anfrage Anfragen Angebot Angebote Rechnung Rechnungen Bestellung Bestellungen Bestätigung
        Vorschau Bildschirm Cursor Mikrofon Sprachausgabe Berechtigung Spracherkennung Handschrift
        Auswahlfläche Benutzerfreundlichkeit Geschwindigkeit Qualität Sicherheit Übersicht Grüße Gruß
        Dank Hilfe Erfolg Geduld Verständnis Interesse Feedback Team Kollegin Kollege Kollegen
        Zusammenarbeit Entscheidung Entscheidungen Nachricht Kommunikation Verbindung Anwendung Anwendungen
        Entwicklung Prüfung Planung Bearbeitung Lieferung Zahlung Betrag Beleg Name Namen Kontakt Kontakte
    """.split(Regex("\\s+")).filter(String::isNotBlank).map(String::lowercase).toSet() - setOf("arbeiten", "antwort", "antworten", "frage", "fragen", "liebe", "grüße")

    val germanSpellings: Set<String> = """
        schön schöne schönen schöner schönes schöneren schönste schon mochte möchte mochten möchten
        konnte könnte konnten könnten musste müsste mussten müssten wurde würde wurden würden
        hätte hätten hätte möchtest könntest müsstest würdest nächstes nächsten nächste nächsten
        möglich mögliche möglichen natürlich für über überhaupt zusätzlich verfügbar zuverlässig
        größer größere größeren größeres groß große großen größtenteils müde glücklich früh früher
        zurück künftig kürzlich ungefähr vollständig regelmäßig möglichst persönlich tatsächlich
        öffnen geöffnet schließen geschlossen prüfen geprüft ergänzen ergänzt unterstützen unterstützt
        berücksichtigen berücksichtigt erklären erklärt gehören gehört hören gehört wünschen gewünscht
        auswählen ausgewählt bestätigen bestätigt verändern verändert verbessern verbessert benötigen benötigt
        unabhängig außergewöhnlich täglich pünktlich freundlich später spät grün grüne grünen süß heiße weiß
        größer schöner länger höher näher öfter häufig völlig ähnlich üblich nützlich nötig nötigsten
        schnellstmöglich öffentlich einfach verständlich gültig gültige gültigen dringend verfügbar verfügbaren
        fröhlich gemütlich überrascht böse bösen frühzeitig mühsam sorgfältig ausführlich grundsätzlich
    """.split(Regex("\\s+")).filter(String::isNotBlank).toSet()

    private val functionWords = "ich wir du er sie es ihr das dass der die den dem ein eine einen einer einem und aber oder weil wenn als ist sind war waren bin bist habe hast hat haben mit von zu für über bitte gerne heute gestern morgen später schon noch auch nicht jetzt".split(' ').toSet()
    private val determiners = setOf("das", "ein", "dein", "mein", "sein", "ihr", "unser", "euer", "beim", "zum", "vom", "guten", "schönen", "die", "der", "eine", "einer", "einen", "einem", "meine", "deine", "ihre", "unsere")
    private val ambiguousNouns = setOf("morgen", "essen", "trinken", "lernen", "lesen", "schreiben", "spielen", "treffen", "leben", "laufen", "denken", "arbeiten", "antwort", "antworten", "frage", "fragen", "liebe", "grüße")
    private val formalPronouns = setOf("Sie", "Ihnen", "Ihr", "Ihre", "Ihren", "Ihrem", "Ihres")

    fun sentenceStart(before: String): Boolean {
        val left = before.trimEnd().trimEnd('"', '\'', '„', '“', '«', '(', '[')
        if (left.isBlank()) return true
        if (before.trimEnd(' ', '\t').endsWith('\n')) return true
        if (left.last() !in ".!?…") return false
        // A dot in common abbreviations does not begin a new sentence.
        return !Regex("(?i)(?:\\b(?:z|b|d|h|bzw|ca|dr|prof|nr|u|a|usw)\\.)$").containsMatchIn(left)
    }

    fun display(word: String, language: String, before: String = "", atSentenceStart: Boolean = false,
        typed: String = "", personal: Boolean = false): String {
        if (word.isBlank()) return word
        if (typed.length > 1 && typed.filter(Char::isLetter).all(Char::isUpperCase)) return word.uppercase()
        if (personal || word.drop(1).any(Char::isUpperCase)) return if (atSentenceStart) title(word) else word
        val lower = word.lowercase()
        if (language == "en" && lower == "i") return "I"
        if (language == "de") {
            if (typed in formalPronouns && lower == typed.lowercase()) return typed
            val previous = Regex("[\\p{L}]+$").find(before.trimEnd())?.value?.lowercase()
            val noun = lower in germanNouns || lower in ambiguousNouns && previous in determiners
            if (noun || atSentenceStart) return title(lower)
            if (lower in functionWords || lower in ambiguousNouns || lower in germanSpellings) return lower
        }
        return if (atSentenceStart || typed.firstOrNull()?.isUpperCase() == true) title(word) else word
    }

    fun phrase(value: String, language: String, before: String, formal: Boolean = false): String {
        var context = before
        return value.split(' ').filter(String::isNotEmpty).joinToString(" ") { token ->
            val letters = token.trim { !it.isLetterOrDigit() && it != '\'' && it != '’' }
            val display = display(letters, language, context, sentenceStart(context),
                typed = if (formal && letters in formalPronouns) letters else "")
            val result = if (letters.isEmpty()) token else token.replaceRange(token.indexOf(letters), token.indexOf(letters) + letters.length, display)
            context += result + " "
            result
        }
    }

    private fun title(word: String) = word.replaceFirstChar { it.uppercase() }
}
