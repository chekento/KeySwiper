package cloud.kosch.keyswiper.language

data class LanguagePack(
    val tag: String,
    val words: Set<String>,
    val commonNext: Map<String, List<String>>,
    val technicalTerms: Set<String> = emptySet()
) {
    fun contains(word: String): Boolean = word.lowercase() in words

    fun prefixMatches(prefix: String, limit: Int = 16): List<String> {
        val normalized = prefix.lowercase()
        if (normalized.isBlank()) return emptyList()

        return words.asSequence()
            .filter { it.startsWith(normalized) && it != normalized }
            .sortedWith(
                compareBy<String> { it.length }
                    .thenBy { it }
            )
            .take(limit)
            .toList()
    }
}

object LanguagePackRegistry {

    private fun words(raw: String): Set<String> =
        raw.split(Regex("\\s+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    private val german = LanguagePack(
        tag = "de",
        words = words("""
            aber alle als also am an andere anderen anders auch auf aus bei bin bis bist bitte
            brauchen braucht da dabei dadurch dafür dagegen daher damit danach dann daran darauf daraus
            darum davon dazu dein deine dem den denn der deren deshalb die diese diesem diesen dieser dieses
            direkt doch dort durch ein eine einem einen einer eines einfach eigentlich entweder er erst erste
            ersten es etwas fast fertig für gegen genau gerade gerne gibt gleich gut gute guten habe haben
            heute hier ich ihr ihre im immer in ist ja jede jeden jeder jedes jetzt kann kannst können könnte
            könnten machen macht mal man mehr mein meine meiner mich mir mit muss müssen möchte möchten
            möglich nach nächste nächsten natürlich nein neue neuen nicht nichts noch nun nur ob oder ohne
            okay richtig schon sehr sein seine selbst sich sie sind so soll sollen sollte sollten später
            super trotzdem über um und uns unser unsere unter vielleicht vom von vor warum was weiter welche
            welcher welches wenn werde werden wie wieder wir wird wirklich wo wohl würde würden zu zuerst
            zusammen zum zur zwischen
            arbeit antwort app auswahl beispiel benutzer build code daten datei details download eingabe
            einstellung email ergebnis fehler feature funktion github idee information intelligent intelligenz
            kontext korrektur leiste lernen lokal lösung modell nachricht option prediction profil projekt
            quelle satz settings sprache swipe tastatur text übersetzung version vorschlag vorschläge wort
            wörter
        """),
        commonNext = mapOf(
            "ich" to listOf("möchte","kann","habe","bin","würde","denke"),
            "wir" to listOf("können","haben","sollten","müssen","werden"),
            "das" to listOf("ist","kann","wäre","sollte","funktioniert"),
            "die" to listOf("tastatur","vorschläge","app","version","funktion"),
            "bitte" to listOf("weiter","prüfen","machen","noch","auch"),
            "mehr" to listOf("kontext","details","intelligenz","optionen","sprachen"),
            "kontext" to listOf("kennen","nutzen","berücksichtigen","verstehen"),
            "vorschläge" to listOf("sollen","können","werden","passen"),
            "prediction" to listOf("soll","kann","nutzt","lernt"),
            "build" to listOf("ist","läuft","wurde","soll","braucht")
        ),
        technicalTerms = words("""
            android apk api build cloud code github gradle ime kotlin litert litertlm llm mlkit model
            prediction prompt repository sdk swipe ui ux
        """)
    )

    private val english = LanguagePack(
        tag = "en",
        words = words("""
            a about after again against all also am an and another any are around as ask at available
            back be because been before being better between both build but by can change check could current
            day did different do does done down each easy either else enough even every example first for
            from further get give go good great had has have he hello help here how i if important in include
            into is it its just know last later let like make many may me more most much must my need new
            next no not now of off often okay on one only or other our out over part people please possible
            really right same see should so some something still such sure take than that the their them then
            there these they thing think three this those through time to today together too try under up use user
            very want was way we well were what when where which while who why will with without work would
            yes yet you your
            answer app application build candidate code context correction data details download email error
            feature file function github idea input intelligence keyboard language learning local message
            model option prediction profile project release result sentence settings source suggestion
            suggestions swipe text translation version word words
        """),
        commonNext = mapOf(
            "i" to listOf("want","would","can","have","need","think"),
            "we" to listOf("can","should","need","have","want","will"),
            "this" to listOf("is","can","would","should","works"),
            "the" to listOf("keyboard","suggestions","app","model","version"),
            "please" to listOf("continue","check","add","make","also"),
            "more" to listOf("context","details","intelligence","options","languages"),
            "context" to listOf("aware","helps","matters","improves","should"),
            "suggestions" to listOf("should","can","will","need","be"),
            "prediction" to listOf("should","can","uses","learns","needs"),
            "build" to listOf("is","works","failed","should","needs")
        ),
        technicalTerms = words("""
            android apk api build cloud code github gradle ime kotlin litert litertlm llm mlkit model
            prediction prompt repository sdk swipe ui ux
        """)
    )

    private val italian = LanguagePack(
        tag = "it",
        words = words("""
            a ad adesso anche ancora ciao andare altro allora al alla alle allo bene bisogno buona buono che chi
            ci come con cosa da dal dalla dare dei del della delle devo di dire dopo dove due e è esempio
            essere fa fare fino fra grazie ha hai hanno ho i il in io la le lo loro ma me meglio meno mio
            molto nei nel nella no noi non nostro nuova nuovo o oggi ogni ora per perché più poi posso
            possibile prima può quando questo questa qui quindi senza se sei siamo sì solo sono sopra sua
            suo tra tutto un una uno usare va voglio voi vostro
            android app build codice contesto correzione dati dettaglio download email errore funzione github
            input intelligenza lingua messaggio modello opzione prediction progetto risultato frase settings
            suggerimento suggerimenti swipe tastiera testo traduzione versione parola parole
        """),
        commonNext = mapOf(
            "io" to listOf("voglio","posso","ho","sono","penso"),
            "noi" to listOf("possiamo","dobbiamo","vogliamo","abbiamo"),
            "questo" to listOf("è","può","sarebbe","funziona"),
            "la" to listOf("tastiera","prediction","versione","funzione"),
            "più" to listOf("contesto","dettagli","opzioni","lingue"),
            "contesto" to listOf("conoscere","usare","capire","considerare")
        ),
        technicalTerms = words("""
            android apk api build cloud code github gradle ime kotlin litert litertlm llm mlkit model
            prediction prompt repository sdk swipe ui ux
        """)
    )

    private val french = LanguagePack(
        tag = "fr",
        words = words("""
            à afin alors aussi autre avec avoir bien bon bonne ça ce ces cette ceux chaque chez comme comment
            dans de des deux doit donc du elle en encore est et être exemple faire fait faut fois grâce ici
            il ils je la le les leur mais me mieux moins mon ne non notre nous nouveau nouvelle maintenant
            où oui par parce pas peut plus pour pourquoi quand que quel quelle quelques qui rien sans se
            seulement si son sont sous sur très tu un une utiliser va veux votre vous
            android app build clavier code contexte correction données détail téléchargement email erreur
            fonction github intelligence langue message modèle option prediction projet résultat phrase
            settings suggestion suggestions swipe texte traduction version mot mots
        """),
        commonNext = mapOf(
            "je" to listOf("veux","peux","suis","pense","voudrais"),
            "nous" to listOf("pouvons","devons","voulons","avons"),
            "ce" to listOf("est","peut","serait","fonctionne"),
            "le" to listOf("clavier","contexte","modèle","texte"),
            "plus" to listOf("contexte","détails","options","langues"),
            "contexte" to listOf("connaître","utiliser","comprendre","considérer")
        ),
        technicalTerms = words("""
            android apk api build cloud code github gradle ime kotlin litert litertlm llm mlkit model
            prediction prompt repository sdk swipe ui ux
        """)
    )

    private val spanish = LanguagePack(
        tag = "es",
        words = words("""
            a ahora algo algún alguna hola algunos antes aquí así aunque bien bueno buena cada cómo con cosa cuando
            de del desde después donde dos el ella en entre es esa ese esta este esto ejemplo hacer hasta hay
            hoy la las le lo los más me mejor menos mi mientras muy nada necesito ni no nos nosotros nueva
            nuevo o otra otro para pero poco por porque puede puedo que qué quien quiero se sea según ser si
            sí sin sobre solo son su también tener todo tu un una usar va vamos ver vez y ya yo
            android app build código contexto corrección datos detalle descarga email error función github
            inteligencia idioma mensaje modelo opción prediction proyecto resultado frase settings sugerencia
            sugerencias swipe teclado texto traducción versión palabra palabras
        """),
        commonNext = mapOf(
            "yo" to listOf("quiero","puedo","tengo","soy","pienso"),
            "nosotros" to listOf("podemos","debemos","queremos","tenemos"),
            "esto" to listOf("es","puede","sería","funciona"),
            "el" to listOf("teclado","contexto","modelo","texto"),
            "más" to listOf("contexto","detalles","opciones","idiomas"),
            "contexto" to listOf("conocer","usar","entender","considerar")
        ),
        technicalTerms = words("""
            android apk api build cloud code github gradle ime kotlin litert litertlm llm mlkit model
            prediction prompt repository sdk swipe ui ux
        """)
    )

    val all: List<LanguagePack> =
        listOf(german, english, italian, french, spanish)

    fun get(tag: String): LanguagePack? =
        all.firstOrNull {
            it.tag == tag.substringBefore('-').lowercase()
        }

    fun languagesForWord(word: String): Set<String> {
        val normalized = word.lowercase()
        return all.asSequence()
            .filter {
                normalized in it.words ||
                    normalized in it.technicalTerms
            }
            .map { it.tag }
            .toSet()
    }
}
