# Prediction Engine

## Architecture

KeySwiper prediction is intentionally split into latency layers.

### 1. Instant layer

The instant layer is called after normal typing events and must stay responsive without waiting for network or model startup.

It currently provides:

- partial-word completion;
- next-word suggestions;
- language-aware vocabulary;
- local personal 2/3/4-gram learning;
- selected-suggestion reinforcement;
- short curated phrase continuations.

### 2. Semantic layer

The semantic layer performs local beam search over multiple possible word sequences. It can plan between 2 and 6 words ahead and merges:

- personal learned followers;
- multilingual transition graphs;
- input-mode priors;
- per-step ranking priors;
- language hints.

By default the semantic depth is 5 words. It can be changed in Settings.

### Hybrid ranking

The visible strip normally keeps:

- up to four fast single-word/completion suggestions;
- up to two longer semantic sentence continuations.

This prevents multi-word predictions from crowding out useful one-tap next-word choices.

## Input context modes

KeySwiper classifies the current Android editor locally using `EditorInfo`:

- General
- Message
- Email
- Search
- Code

The mode changes ranking priors. It does not upload the app name or typed text.

## Personal language memory

The local memory stores derived word-transition counts. v0.5 adds four-word context, allowing a known three-word prefix to influence the next token more strongly than a generic bigram.

The memory is bounded and prunes low-frequency entries.

Sensitive/password and no-personalized-learning fields bypass prediction context and learning.

## Neural provider architecture

`NeuralPredictionBackend` is now a stable integration boundary for a future downloaded local language model.

The keyboard does not require a neural model to function.

Preferred future path:

1. download a compatible `.litertlm` model separately from the APK;
2. validate storage, device memory and accelerator support;
3. initialize LiteRT-LM only when the device can support it;
4. ask the neural provider for a small number of semantic continuations;
5. merge neural suggestions with the instant and beam-search results;
6. fall back instantly when the model is absent, busy or too slow.

This avoids shipping a very large language model inside every APK.

## Gemini Nano / AICore

Gemini Nano remains useful for explicit foreground GenAI features, but KeySwiper does not depend on it for always-on prediction because current AICore GenAI inference is restricted to the top foreground application. An IME normally serves another foreground app.

## Future work

- downloadable LiteRT-LM model manager;
- model-size tiers;
- neural reranking of swipe candidates;
- cross-language code-switching model;
- punctuation prediction;
- named-entity and contact-aware suggestions with explicit permission;
- domain profiles such as developer, business, gaming and creative writing;
- confidence calibration from real correction outcomes.


## v0.7 multilingual lanes

The instant layer now queries up to three simultaneous language lanes rather than selecting one global language.

The same lane order is shared with swipe recognition. Personal vocabulary sits above the embedded packs and can introduce user-specific words without rebuilding the application.

See [Code Switching](CODE_SWITCHING.md).


## Input-language lock (0.17)

Prediction and swipe language handling are intentionally different.

For word/sentence prediction, KeySwiper resolves one primary input language from the current token, the most recent identifiable words, ML Kit language hints and finally the active keyboard layout. Local learned followers, semantic beam candidates and optional neural candidates are filtered against that primary language. This prevents German input from suddenly receiving English suggestions, and vice versa.

Swipe decoding keeps broader language lanes so deliberate code-switching can still work without forcing a layout change.

Asynchronous language-identification results are generation-guarded: an older detector callback cannot replace the language state of newer text.


## Contextual corrections and completions (0.19)

Local typo candidates use a weighted edit distance with adjacent-key substitutions, transposed letters, accents and German ae/oe/ue transliterations. Preceding-word transitions and bounded personal context break close ties. Prefix completion can find umlauts from a plain vowel, and candidate text follows the capitalization already typed.

Only an unambiguous, high-confidence transposition, adjacent-key or accent/transliteration candidate is applied on space. Insertion/deletion suggestions remain explicit choices so an unlisted valid inflection is not silently changed to a dictionary stem. Known words across the built-in languages and personal vocabulary are preserved; capitalized unknown words are not silently treated as typos unless the result is an unambiguous common function word. Acronyms, identifiers, URLs, email/number fields, search/code modes, sensitive fields and editors requesting no suggestions are excluded from automatic changes. This is a compact local dictionary and heuristic ranker, not a guarantee that every word or name is understood.

The strip keeps the original spelling as an explicit option. Immediate Backspace restores the original text only if the before/after cursor context and selection still match. Undo suppresses that correction for the rest of the input session. Automatic replacements never train themselves; explicitly selected suggestions and intentionally retained words can still be learned. Autocorrection can be disabled in Settings.

Word and phrase labels contain the actual insertion text without decorative arrow/star prefixes. Corrections use an accent border and an accessible correction label. Unavailable Undo/Redo controls leave room for the words.

## Word boundaries after acceptance (0.19.1)

Swipe words and accepted completion/correction/phrase chips use the same batched editor transaction. It finishes an outstanding composing span, replaces only the intended prefix or selection, commits the text with a real trailing space and positions the cursor after that space. An existing ordinary space immediately after the cursor is reused rather than duplicated. Newlines and additional indentation are preserved. A new swipe after a manually typed word adds the missing boundary before the new word as well.

Pure spacing tests and Android editable/cursor checks verify that a subsequent key starts the next word without pressing Space. Successful insertion is required before learning or updating the accepted-word state.

## Editing inside words (0.20)

The current word includes its prefix before the cursor and its suffix after it. Typo candidates consider the complete token; completions receive a preference for preserving the existing suffix and fitting the following word. Accepting a replacement removes both parts of the original token, reuses the next ordinary separator and leaves the cursor after the completed word. Explicit selections continue to take precedence. Learned vocabulary retains its preferred display spelling, including internal capitals. These are local ranking improvements; predictions can still be wrong, and ambiguous corrections remain choices.

## Context and orthography (0.21)

The local correction engine now keeps accent-only alternatives for valid words, so `schon → schön` and `mochte → möchte` remain suggestions without an automatic meaning change. Noun and sentence casing is applied through a shared spelling policy, with contextual handling for ambiguous words such as `morgen`, personal names and formal pronouns. The suggestion identity preserves meaningful case differences, including the original spelling.

Local sentence prediction uses a multilingual phrase corpus with domain/register hints, matching up to six prefix words. Topics score compatible phrases instead of injecting unrelated words into a word graph. Personal n-grams can supply additional continuations; longer contexts take precedence and cached follower indexes are invalidated by learning. Repetition and right-hand overlap are filtered. Default depth is eight words, configurable from two to twelve. These remain bounded local suggestions, not unrestricted language understanding. Optional on-device neural refinement receives explicit casing, negation, register and diversity instructions.
