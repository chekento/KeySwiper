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
