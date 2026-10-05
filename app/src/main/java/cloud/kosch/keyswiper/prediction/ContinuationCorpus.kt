package cloud.kosch.keyswiper.prediction

/** Small local phrase corpus, with domains and register instead of unrelated topic-word injection. */
object ContinuationCorpus {
    data class Entry(val language: String, val mode: PredictionInputMode, val topics: Set<String>, val text: String) {
        val displayWords = text.split(' ')
        val words = displayWords.map(::normalize)
    }
    fun normalize(word: String) = word.lowercase().trim { !it.isLetterOrDigit() && it !in "'’" }
    fun words(text: String) = Regex("[\\p{L}\\p{N}]+(?:[-'’][\\p{L}]+)?").findAll(text).map { it.value.lowercase() }.toList()
    val entries: List<Entry> = """
        de|GENERAL|ergänzen idee|Ich möchte gerne noch etwas ergänzen
        de|GENERAL|verbessern tastatur vorschläge|Ich möchte die Vorschläge weiter verbessern
        de|GENERAL|lernen verstehen|Ich möchte das besser verstehen
        de|GENERAL|frage erklären|Ich möchte gerne mehr darüber erfahren
        de|GENERAL|termin besprechung zeit|Ich möchte gerne einen Termin mit dir vereinbaren
        de|EMAIL|termin besprechung zeit|Ich möchte gerne einen Termin mit Ihnen vereinbaren
        de|MESSAGE|treffen zeit morgen|Wir können uns morgen in Ruhe darüber unterhalten
        de|GENERAL|arbeit projekt aufgabe|Wir können das gemeinsam Schritt für Schritt umsetzen
        de|GENERAL|idee lösung projekt|Wir können das direkt umsetzen
        de|GENERAL|arbeit projekt aufgabe|Wir sollten zuerst die offenen Fragen klären
        de|GENERAL|lösung idee|Das ist eine gute Idee
        de|GENERAL|ergebnis fortschritt|Das ist schon viel besser geworden
        de|GENERAL|verbesserung vorschläge|Die Vorschläge sollen noch besser werden
        de|GENERAL|verbesserung vorschläge|Das soll noch besser werden
        de|GENERAL|freude schön|Das ist wirklich schön
        de|GENERAL|frage unklar|Das kann ich noch nicht genau beurteilen
        de|GENERAL|problem fehler tastatur|Das funktioniert bei mir leider noch nicht
        de|GENERAL|problem fehler|Das Problem tritt nur gelegentlich auf
        de|GENERAL|problem fehler|Ich kann das Problem gerade nicht nachvollziehen
        de|GENERAL|termin absage|Ich kann heute leider nicht an dem Termin teilnehmen
        de|GENERAL|termin absage|Ich kann nicht an dem Termin teilnehmen
        de|MESSAGE|zeit morgen treffen|Ich kann morgen wieder vorbeikommen
        de|MESSAGE|zeit heute treffen|Ich habe heute leider keine Zeit
        de|GENERAL|frage|Ich habe dazu noch eine Frage
        de|GENERAL|idee vorschlag|Ich habe einen anderen Vorschlag
        de|GENERAL|projekt dokument|Ich habe die Unterlagen bereits vorbereitet
        de|MESSAGE|ankunft zug unterwegs|Ich bin gerade unterwegs und melde mich später
        de|MESSAGE|ankunft zug unterwegs|Ich bin in ungefähr zehn Minuten da
        de|MESSAGE|ankunft zuhause|Ich bin gut zu Hause angekommen
        de|GENERAL|termin besprechung|Ich freue mich auf unser Gespräch
        de|MESSAGE|treffen familie|Ich freue mich auf das Wiedersehen
        de|GENERAL|hilfe rückmeldung|Vielen Dank für deine Hilfe
        de|EMAIL|hilfe rückmeldung|Vielen Dank für Ihre Rückmeldung
        de|EMAIL|hilfe anfrage|Vielen Dank für Ihre Nachricht und Ihr Interesse
        de|GENERAL|hilfe verständnis|Vielen Dank für das Verständnis
        de|MESSAGE|hilfe danke|Danke für deine Unterstützung
        de|MESSAGE|hilfe danke|Das hat mir wirklich weitergeholfen
        de|GENERAL|arbeit projekt|Bitte prüfe das noch einmal in Ruhe
        de|GENERAL|fortsetzung projekt|Bitte mach damit weiter
        de|GENERAL|frage dokument|Bitte schick mir die fehlenden Informationen
        de|EMAIL|frage dokument|Bitte senden Sie mir die fehlenden Unterlagen
        de|EMAIL|termin besprechung|Bitte bestätigen Sie mir den vorgeschlagenen Termin
        de|GENERAL|frage erklären|Kannst du mir das bitte genauer erklären
        de|GENERAL|frage problem|Kannst du mir dabei helfen
        de|MESSAGE|treffen zeit|Kannst du mir kurz Bescheid geben
        de|EMAIL|frage dokument|Könnten Sie mir bitte weitere Informationen zusenden
        de|EMAIL|termin zeit|Könnten wir einen neuen Termin vereinbaren
        de|GENERAL|frage problem|Wie können wir das am besten lösen
        de|GENERAL|frage problem|Was genau funktioniert dabei noch nicht
        de|GENERAL|frage projekt|Welche Möglichkeiten haben wir dafür
        de|MESSAGE|frage familie|Wie geht es dir heute
        de|MESSAGE|frage treffen|Wann hast du morgen Zeit
        de|MESSAGE|ankunft treffen|Sag mir bitte kurz Bescheid
        de|MESSAGE|ankunft treffen|Ich melde mich später noch einmal bei dir
        de|EMAIL|rückmeldung kontakt|Ich melde mich schnellstmöglich bei Ihnen
        de|GENERAL|planung aufgabe|Als Nächstes können wir die Details besprechen
        de|GENERAL|planung aufgabe|Danach können wir die nächsten Schritte planen
        de|GENERAL|projekt fertig|Wir haben die wichtigsten Punkte bereits geklärt
        de|GENERAL|projekt frage|Dazu brauche ich noch weitere Informationen
        de|GENERAL|projekt idee|Das wäre eine sinnvolle Ergänzung
        de|GENERAL|zeit termin|Für mich passt der vorgeschlagene Termin gut
        de|MESSAGE|zeit termin|Morgen habe ich wieder etwas mehr Zeit
        de|EMAIL|anhang dokument|Im Anhang finden Sie die angefragten Unterlagen
        de|EMAIL|anhang dokument|Anbei sende ich Ihnen die gewünschten Informationen
        de|EMAIL|frage kontakt|Bei Fragen stehe ich Ihnen gerne zur Verfügung
        de|EMAIL|frage kontakt|Für Rückfragen stehe ich Ihnen gerne zur Verfügung
        de|EMAIL|kontakt grüße|Mit freundlichen Grüßen
        de|MESSAGE|kontakt grüße|Viele liebe Grüße
        de|MESSAGE|kontakt grüße|Ich wünsche dir einen schönen Tag
        de|EMAIL|kontakt grüße|Ich wünsche Ihnen ein schönes Wochenende
        de|GENERAL|tastatur vorschläge eingabe|Die Tastatur soll Wörter zuverlässiger erkennen
        de|GENERAL|vorschläge kontext sprache|Die Vorschläge sollen zum aktuellen Satz passen
        de|GENERAL|vorschläge kontext sprache|Die Vorschläge können den bisherigen Kontext berücksichtigen
        de|GENERAL|kontext sprache|Den Kontext kennen und sinnvoll berücksichtigen
        de|GENERAL|kontext sprache|Den Kontext nutzen und die Vorschläge verbessern
        de|GENERAL|kontext sprache|Den Kontext verstehen und passende Wörter vorschlagen
        de|GENERAL|korrektur großschreibung|Die Korrektur soll Großschreibung und Umlaute berücksichtigen
        de|GENERAL|stift tastatur eingabe|Mit dem Stift kann ich den Cursor genauer platzieren
        de|GENERAL|stift tastatur eingabe|Die Eingabe funktioniert jetzt deutlich zuverlässiger
        de|GENERAL|fehler app version|In der aktuellen Version tritt der Fehler weiterhin auf
        de|GENERAL|fehler app version|Nach dem Update funktioniert alles wieder richtig
        de|GENERAL|termin absage|Leider muss ich unseren Termin verschieben
        de|GENERAL|bestellung lieferung|Ich möchte mich nach meiner Bestellung erkundigen
        de|EMAIL|rechnung zahlung|Die Rechnung habe ich bereits bezahlt
        de|GENERAL|frage erklärung|Das bedeutet für mich einen wichtigen Unterschied
        de|GENERAL|frage erklärung|Ein gutes Beispiel dafür ist die folgende Situation
        en|GENERAL|idea addition|I would like to add one more thing
        en|GENERAL|understand question|I would like to understand this better
        en|GENERAL|meeting appointment time|I would like to arrange a meeting with you
        en|GENERAL|task project work|We can work through this together step by step
        en|GENERAL|task project work|We can do that directly
        en|GENERAL|idea solution|This is a good idea
        en|GENERAL|idea solution|This could be a useful improvement
        en|GENERAL|problem error|This does not seem to work yet
        en|GENERAL|problem error|I cannot reproduce the problem right now
        en|GENERAL|meeting cancellation|I cannot attend the meeting today
        en|GENERAL|question|I have one more question about this
        en|GENERAL|project documents|I have already prepared the documents
        en|MESSAGE|arrival travel|I am on my way and will be there soon
        en|MESSAGE|time travel|I will get back to you later today
        en|MESSAGE|arrival home|I got home safely
        en|GENERAL|thanks help|Thank you for your help
        en|EMAIL|thanks reply|Thank you for your message and your interest
        en|EMAIL|thanks reply|Thank you for getting back to me
        en|GENERAL|question explain|Could you explain that in more detail
        en|GENERAL|question help|Could you please help me with this
        en|GENERAL|question problem|How can we solve this together
        en|GENERAL|question problem|What exactly is not working yet
        en|GENERAL|request documents|Please send me the missing information
        en|GENERAL|continue project|Please continue with the next step
        en|GENERAL|continue project|Please check this one more time
        en|GENERAL|plan project|We should clarify the remaining questions first
        en|GENERAL|plan project|We can discuss the next steps tomorrow
        en|EMAIL|attachment document|Please find the requested documents attached
        en|EMAIL|meeting time|The proposed time works well for me
        en|EMAIL|meeting time|Could we arrange another meeting
        en|EMAIL|question contact|Please let me know if you have any questions
        en|EMAIL|question contact|I look forward to hearing from you
        en|MESSAGE|question family|How are you doing today
        en|MESSAGE|time meeting|When would be a good time for you
        en|MESSAGE|greetings family|I hope you have a wonderful day
        en|GENERAL|keyboard suggestions context|The suggestions should fit the current sentence
        en|GENERAL|keyboard input|The keyboard should recognize words more reliably
        en|GENERAL|context language|The context helps us choose the right words
        en|GENERAL|error version|The issue still occurs in the current version
        en|GENERAL|error version|Everything works again after the update
        it|GENERAL|idea progetto|Io voglio aggiungere ancora una cosa
        it|GENERAL|idea progetto|Voglio continuare con il prossimo passo
        it|GENERAL|idea progetto|Noi possiamo farlo insieme passo dopo passo
        it|GENERAL|domanda aiuto|Puoi spiegarmi meglio questo punto
        it|GENERAL|grazie aiuto|Grazie per il tuo aiuto
        it|MESSAGE|tempo incontro|Ci possiamo sentire domani con calma
        it|MESSAGE|arrivo|Sono in viaggio e arrivo tra poco
        it|EMAIL|documenti|Le invio i documenti richiesti in allegato
        it|EMAIL|domanda contatto|Resto a disposizione per ulteriori informazioni
        it|GENERAL|contesto lingua|Il contesto aiuta a scegliere le parole giuste
        fr|GENERAL|idée projet|Je voudrais ajouter encore une chose
        fr|GENERAL|idée projet|Je veux continuer avec la prochaine étape
        fr|GENERAL|idée projet|Nous pouvons faire cela ensemble étape par étape
        fr|GENERAL|question aide|Peux-tu expliquer ce point plus en détail
        fr|GENERAL|merci aide|Merci pour votre aide
        fr|MESSAGE|heure rendezvous|Nous pouvons en parler demain tranquillement
        fr|MESSAGE|arrivée|Je suis en route et arrive bientôt
        fr|EMAIL|documents|Vous trouverez les documents demandés en pièce jointe
        fr|EMAIL|question contact|Je reste disponible pour toute information complémentaire
        fr|GENERAL|contexte langue|Le contexte aide à choisir les bons mots
        es|GENERAL|idea proyecto|Me gustaría añadir una cosa más
        es|GENERAL|idea proyecto|Yo quiero seguir con el siguiente paso
        es|GENERAL|idea proyecto|Nosotros podemos hacerlo juntos paso a paso
        es|GENERAL|pregunta ayuda|Puedes explicar este punto con más detalle
        es|GENERAL|gracias ayuda|Gracias por tu ayuda
        es|MESSAGE|hora reunión|Podemos hablar de esto mañana con calma
        es|MESSAGE|llegada|Estoy de camino y llegaré pronto
        es|EMAIL|documentos|Le envío los documentos solicitados como archivo adjunto
        es|EMAIL|pregunta contacto|Quedo a su disposición para cualquier consulta
        es|GENERAL|contexto idioma|El contexto ayuda a elegir las palabras adecuadas
    """.trimIndent().lines().filter(String::isNotBlank).map { line ->
        val parts = line.trim().split('|', limit = 4)
        Entry(parts[0], PredictionInputMode.valueOf(parts[1]), parts[2].split(' ').toSet(), parts[3])
    }
    fun contextMatch(language: String, history: List<String>, candidate: String): Int {
        if (history.isEmpty()) return 0
        var best = 0
        for (entry in entries) {
            if (entry.language != language) continue
            for (index in 1 until entry.words.size) {
                if (entry.words[index] != candidate.lowercase()) continue
                for (size in minOf(index, history.size, 5) downTo best + 1) {
                    if (history.takeLast(size).map(String::lowercase) == entry.words.subList(index - size, index)) {
                        best = size
                        break
                    }
                }
            }
        }
        return best
    }
}
