# Changelog

## 0.6.0-alpha06 — 2026-09-27

Surrounding Context Intelligence + LiteRT-LM model manager:

- prediction context now reads both text before and text after the cursor
- selected text is included as a local context signal
- current sentence, previous sentence, following sentence and current paragraph are extracted locally
- local topic-term extraction ranks context-related continuations more strongly
- question intent is detected and can affect semantic ranking
- semantic beam search v2 uses the current sentence rather than only a short trailing word list
- semantic candidates can bridge toward existing text after the cursor and suppress obvious duplication
- Android API 31+ uses InputConnection.getSurroundingText with legacy before/after/selection fallbacks
- surrounding context remains completely disabled in sensitive/password/no-learning fields
- neural prompts receive only the local surrounding snapshot when an optional local model is installed
- LiteRT-LM Android 0.17.1 runtime integrated
- local .litertlm model manager added with private-app-storage import, SHA-256 hashing, verification and removal
- neural inference runs on a dedicated background executor and never blocks normal keyboard input
- stale queued neural prediction jobs are discarded in favor of the newest context
- stale neural results cannot overwrite newer prediction-strip state
- fast local suggestions remain visible while neural refinement runs
- optional neural suggestions are marked with ✦ and merged with local word/sentence predictions
- LiteRT-LM model import UI added to Settings
- optional native GPU libraries declared as non-required
- release ProGuard keep rules added for LiteRT-LM/JNI integration
- minimum Android version raised from API 23 to API 24 for LiteRT-LM compatibility
- CI build JDK raised to 21 while app source compatibility remains Java 17
- new tests cover surrounding-context extraction, topic extraction, question detection, contextual semantic generation and neural/local merge
- version advanced to 0.6.0-alpha06 / build 6

## 0.5.0-alpha05 — 2026-09-27

Hybrid semantic prediction:

- new two-layer prediction architecture: instant local suggestions plus semantic multi-word prediction
- local semantic beam search can plan 2–6 words ahead
- semantic prediction depth is configurable in Settings, default 5 words
- local personal prediction learning expanded from bigrams/trigrams to 2/3/4-gram context
- longer personal context receives progressively higher ranking weight
- semantic beam search mixes learned personal followers with language transition graphs
- candidate beams are scored by rank prior, personal context and input-mode relevance
- prediction input context is classified locally as General, Message, Email, Search or Code using Android EditorInfo
- mode-aware ranking can favor message, email, search or code vocabulary without uploading app content
- hybrid engine keeps up to four fast word suggestions plus up to two longer semantic continuations
- neural prediction backend contract added for future downloadable model providers
- LiteRT-LM selected as the preferred future local neural path rather than making Gemini Nano/AICore mandatory for the IME
- new beam-search unit tests cover multi-word continuation, partial-token suppression and hybrid word/sentence output
- version advanced to 0.5.0-alpha05 / build 5

## 0.4.0-alpha04 — 2026-09-27

Intelligent word and sentence prediction:

- new dedicated prediction strip placed exactly between the toolbar and keyboard
- prediction strip dynamically switches between normal prediction and swipe-correction candidates
- local word completion while a token is being typed
- next-word prediction from language-specific context maps
- local bigram and trigram learning from confirmed text
- personal learned followers outrank generic suggestions over time
- sentence-continuation suggestions appear as longer arrow-prefixed chips
- prediction ranking uses Google ML Kit language hints from surrounding text
- German, English, Italian, French and Spanish starter prediction packs
- personal transition memory can learn additional vocabulary independent of starter language packs
- selecting a prediction teaches the local transition model
- sensitive/password fields disable prediction context and prediction learning
- dedicated Settings reset for word/sentence prediction memory
- prediction engine unit tests cover word completion, next-word prediction and sentence continuation
- Gemini Nano Prompt API evaluated but not used as always-on IME predictor because current AICore foreground restrictions can block inference from an IME service
- version advanced to 0.4.0-alpha04 / build 4

## 0.3.0-alpha03 — 2026-09-27

Geometric adaptive swipe engine:

- swipe gestures now capture normalized X/Y/time samples instead of only touched key letters
- swipe traces retain up to 96 path points for low-overhead geometric analysis
- candidate ranking now includes Dynamic Time Warping between the user path and ideal QWERTY word paths
- path-length similarity is included in candidate scoring
- direction changes are compared against the expected word path
- timestamp-derived speed is used for velocity-aware key/corner scoring
- explicit candidate corrections train a local per-letter motor offset profile
- motor offsets use an adaptive exponential moving average and remain bounded to avoid model drift
- geometry and motor-profile learning remain disabled in password/no-learning fields
- Settings can reset both lexical swipe learning and the geometric motor profile together
- new geometry unit tests cover ideal-vs-wrong word paths and shifted motor traces
- version advanced to 0.3.0-alpha03 / build 3

## 0.2.0-alpha02 — 2026-09-27

Swipe v2 and permanent build archive:

- adaptive local swipe learning from explicit candidate corrections
- Google ML Kit automatic language identification feeds the swipe ranking context
- multilingual starter lexicons for German, English, Italian, French and Spanish
- endpoint-tolerant swipe ranking instead of requiring exact first/last-key matches
- sentence-start capitalization for swipe candidates
- first Backspace after a swipe removes the complete just-swiped word
- local swipe-learning reset control in Settings
- password / sensitive fields remain excluded from adaptive learning
- every successful main-branch APK build is permanently archived as a GitHub Release
- every archived APK receives a dedicated version page with commit, SHA-256 and changelog
- CI artifacts now include version, build code and CI run number

## 0.1.0-alpha01 — 2026-09-27

Initial KeySwiper foundation:

- Android IME service
- tap keyboard and swipe-path capture
- first candidate decoder scaffold
- candidate replacement strip
- local session clipboard panel
- emoji panel
- Android Voice2Text integration with on-device preference
- Google ML Kit language identification + on-device translation
- Google ML Kit Digital Ink handwriting foundation
- stylus-aware input and local S Pen button handling
- sensitive-field privacy policy
- language settings screen
- CI debug APK build
