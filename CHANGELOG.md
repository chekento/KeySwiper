# Changelog

## 0.17.0-alpha17 — 2026-09-28

Compact input, language-locked prediction, stylus intent and five new themes:

- capped the normal keyboard UI at one third of the physical display height, including navigation-bar inset budget
- replaced the persistent in-keyboard status strip with transient status toasts so status messages no longer add another bar above the keys
- added navigation-bar inset handling and consistent IME navigation-bar theming to prevent system chrome from covering the keyboard surface
- reduced toolbar, prediction strip, accent row and bottom-row height proportionally while preserving the maximum one-third constraint
- added a dedicated touch/stylus gesture-intent classifier
- stylus movement now needs a substantially larger deliberate path before KeySwiper treats it as swipe input
- non-swipe stylus motion commits the key selected at pen-down, preventing small pen jitter from becoming a swipe or a neighboring key
- finger swipe thresholds remain more responsive than stylus thresholds
- word and sentence prediction now resolve one primary input language from the current token/recent input, detected language and active keyboard layout
- learned followers, semantic beam candidates and optional neural candidates are language-gated to the active input language
- stale asynchronous language-detection callbacks can no longer overwrite newer typing context
- broad multilingual lanes remain available to swipe decoding for intentional code-switching
- added five selectable themes: OLED Obsidian, Neon Tokyo, Aurora Glass, Ember Copper and Kawaii Cyber
- added unit tests for stylus tap-vs-swipe intent, one-third keyboard sizing and language-locked prediction
- moved the Android 15 runtime smoke test from Google ATD to lean AOSP ATD after repeated unrelated Google/phone service ANRs stole test focus
- hardened the debug smoke host with repeated IME show requests and focus recovery so CI tests KeySwiper rather than transient system-dialog churn
- advanced Android package version to 0.17.0-alpha17 / build 17

## 0.16.0-alpha16 — 2026-09-28

Stability and automated runtime validation:

- Added an Android 15 Google ATD emulator test that installs the debug APK, enables/selects KeySwiper, opens a focused text field and confirms the IME becomes current and visible.
- Replaced the first instrumentation-based IME smoke test after Android killed its own test process while rebinding the target package as an input method.
- The runtime harness now executes from ADB outside the app process, matching the real system IME lifecycle more closely.
- Added a debug-only smoke host activity; it is not included in release APKs.
- Smoke failures capture input-method state, focused windows, activity state and filtered logcat diagnostics.
- Unit tests, Android Lint and APK compilation now run before emulator validation so a flaky emulator cannot mask core build health.
- Advanced Android package version to 0.16.0-alpha16 / build 16.

## 0.15.0-alpha15 — 2026-09-28

Daily-driver core + Matrix Cyber UI overhaul:

- confirmed Android IME registration flow: KeySwiper now detects installed, enabled and currently selected states instead of showing static setup buttons
- Settings refresh the IME state automatically after returning from Android/Samsung keyboard settings
- step 2 refuses to open the picker prematurely and routes back to Android keyboard settings when KeySwiper is still disabled
- Daily-driver readiness panel added with Android/API, IME, microphone, theme and one-hand status
- concrete real-device test route added directly to Settings
- session-local Autocorrect Timeline added for explicit prediction, swipe-candidate and voice replacements
- dedicated ↶ Undo and ↷ Redo controls added beside the prediction strip
- long-press ↶ opens the recent correction timeline
- Undo/Redo validates the exact cursor-tail context before editing, preventing stale history from deleting text at the wrong location
- new corrections clear redo history like a conventional editor timeline
- correction history is never active in sensitive/password fields and is cleared between input sessions
- one-hand modes added: full width, left and right
- long-press on the keyboard toolbar icon cycles one-hand mode directly
- one-hand mode is also configurable in Settings and persists locally
- Developer layout added behind the </> toolbar control
- Developer layout includes Esc, Tab, brackets, braces, parentheses, angle brackets, slash/backslash, pipe, tilde, backtick, quotes, arrows, Home/End and Ctrl+A/C/V/X/Z/Y
- Developer layout sends normal Android InputConnection key events for app compatibility
- Android handwriting edit gestures expanded beyond scratch-out deletion
- circle gesture maps to Android SelectGesture when the target editor advertises support
- long horizontal gesture maps to RemoveSpaceGesture when supported
- long vertical gesture maps to JoinOrSplitGesture when supported
- unsupported handwriting edit gestures remain ordinary handwriting instead of being consumed
- Matrix Cyber introduced as the default keyboard theme through a reusable theme-profile architecture
- theme preference infrastructure added so future visual themes can be added without rewriting keyboard logic
- keyboard background changed to a near-black Android-compatible cyber surface with restrained neon-mint accents
- letter keys now use rounded 12dp surfaces, subtle borders and pressed states
- keyboard haptic feedback added through Android KEYBOARD_TAP feedback so device haptic settings remain authoritative
- letter rows now distribute across the usable keyboard width instead of using fixed ten-slot centering
- adaptive swipe geometry updated to the same full-width physical key centers used by the rendered keyboard
- Backspace moved into the letter/symbol matrix at the end of the bottom character row, directly after M in QWERTZ/QWERTY layouts
- bottom utility row no longer wastes width on a second Backspace key
- Emoji removed from the toolbar and moved immediately left of the Space key for normal text/email/URL input
- Toolbar redesigned as a shorter, quieter control rail with compact cyber-surface controls
- prediction chips, Undo/Redo controls and auxiliary panels now share the rounded Matrix Cyber visual language
- Clipboard, Translation, Emoji, Handwriting and Developer panels receive consistent themed controls instead of falling back to large default gray buttons
- Android navigation bar beneath the IME is styled to the active keyboard background and navigation-bar contrast enforcement is disabled on supported versions
- Settings keeps Android-style light system bars so app configuration remains native-looking
- new unit tests cover edit timeline safety, redo invalidation, extended handwriting gestures and full-width swipe geometry
- version advanced to 0.15.0-alpha15 / build 15

## 0.14.0-alpha14 — 2026-09-28

Daily-driver hardening:

- keyboard now classifies focused editors as Text, Email, URL or Number/Phone
- number and phone editors open directly on the numeric/symbol surface
- email editors expose a direct @ key in the bottom row
- URL editors expose a direct / key in the bottom row
- numeric/phone editors suppress word and sentence predictions
- focused-editor changes always return KeySwiper from Clipboard/Emoji/Translation panels to the main keyboard
- temporary clipboard search state is reset when a new input field starts
- Backspace now deletes an active text selection before considering swipe undo
- normal Backspace is grapheme-aware instead of blindly deleting one UTF-16 code unit
- surrogate-pair emoji deletion no longer leaves half a character
- combining-character sequences are deleted as one text unit where Java/Android BreakIterator exposes the grapheme boundary
- external cursor/selection movement triggers a debounced prediction/context refresh
- context refresh after cursor movement also updates automatic sentence capitalization
- sentence-start auto-shift added for normal text editors
- auto-shift is disabled for Email, URL and Number/Phone editors
- new input fields close stale auxiliary keyboard panels
- new unit tests cover emoji/combining-character backspace boundaries and editor-mode classification
- Android lint added to CI before APK assembly so release builds now require tests + lint + compile success
- version advanced to 0.14.0-alpha14 / build 14

## 0.13.0-alpha13 — 2026-09-28

Voice Editing v1:

- short tap on the microphone remains normal Voice2Text dictation
- long-press on the microphone now starts a dedicated Voice Editing command mode
- command mode never inserts unrecognized speech as normal text
- German and English voice commands supported in the first command set
- delete-last-word commands supported
- delete-last-sentence commands supported
- new-line / line-break commands supported
- select-all, copy, cut and paste voice commands mapped to Android editor context actions
- replace command supports patterns such as “Ersetze Peter durch Markus” and “replace Peter with Markus”
- replace operates on the most recent matching text before the cursor and preserves following text before the cursor
- undo-last-swipe voice command added
- selected text can be translated by voice command, including “Übersetze ins Englische” / “translate to German”
- common German and English language names are mapped to translation language tags
- translation voice commands continue to use the existing explicit ML Kit translation path
- normal dictation now feeds confirmed words into the local personal vocabulary
- normal dictation prefers the current detected language hint before falling back to the device locale
- voice editing remains completely disabled in sensitive/password fields
- long-press microphone handling consumes the long-click so normal dictation is not triggered afterward
- obsolete unused AndroidX import removed from VoiceInputController
- parser unit tests cover German/English editing commands, replacements, translation targets and unknown speech
- version advanced to 0.13.0-alpha13 / build 13

## 0.12.0-alpha12 — 2026-09-28

Smart Clipboard 2.0:

- clipboard history upgraded from plain strings to structured entries
- automatic local categories: Link, Email, Phone, Address, Code and Text
- clipboard search added directly inside the keyboard
- search matches both clipboard text and category names
- clipboard items can be pinned or unpinned individually
- only explicitly pinned clipboard entries persist across KeySwiper process restarts
- pinned entries are stored in KeySwiper private app storage
- normal unpinned clipboard history remains ephemeral
- unpinned entries receive configurable expiration instead of living for the full process lifetime
- quick expiry presets added: 10 minutes, 1 hour and 1 day
- default clipboard expiry is persisted locally
- Clear removes only unpinned entries and preserves pinned data
- individual clipboard items can be deleted explicitly
- pinned items sort above temporary history
- duplicate clipboard captures are deduplicated through a stable SHA-256-derived local ID
- clipboard capture now uses the real application Context when coercing clipboard text
- history increased to up to 40 entries while retaining pinned entries preferentially
- clipboard text is locally capped at 12,000 characters per entry
- clipboard insertion refreshes language hints and prediction context
- clipboard remains inaccessible from KeySwiper in sensitive/password fields
- new unit tests cover Link, Email, Phone, Address, Code and Text classification
- version advanced to 0.12.0-alpha12 / build 12

## 0.11.0-alpha11 — 2026-09-28

Daily typing usability + translation target picker:

- keyboard layout system added with German QWERTZ, English QWERTY, French AZERTY, Italian QWERTY and Spanish QWERTY
- keyboard defaults to a layout matching the device language where possible
- active keyboard layout can be selected in Settings
- active layout can also be cycled directly from the compact language key below the letter rows
- German layout includes direct ä, ö, ü and ß keys
- French, Italian and Spanish layouts expose their most useful accented characters in a compact language row
- English layout keeps a compact case-control key
- dedicated ?123 number/symbol page added with ABC return key
- swipe decoding is disabled on the symbol page to prevent accidental word gestures
- SwipeTrace now records the physical layout ID
- geometric swipe scoring now resolves ideal key centers against the active QWERTZ/QWERTY/AZERTY layout
- personal motor-offset learning now uses the active physical keyboard layout
- translation target picker added directly to the keyboard toolbar
- translation picker shows current target, recent targets, detected source-language hints and common quick targets
- recently selected translation targets are persisted locally and ranked first
- tapping 🌐 with no selection now opens the target picker instead of only showing an error
- translation remains explicit and applies only to selected text
- Android editor actions are now respected by the Enter key: Search, Send, Go, Done, Next etc. use performEditorAction when available
- normal Enter remains the fallback when the target editor exposes no action
- new unit tests cover QWERTZ/AZERTY profiles, layout-aware geometry and the symbol page
- version advanced to 0.11.0-alpha11 / build 11

## 0.10.0-alpha10 — 2026-09-28

Connectionless handwriting + Android handwriting gestures v1:

- Android 15+ connectionless stylus handwriting sessions implemented
- connectionless handwriting supports delegated/search-style input flows without requiring an active InputConnection
- recognized connectionless handwriting is accumulated locally and returned through finishConnectionlessStylusHandwriting
- connectionless sessions reuse the same local ML Kit Digital Ink model and transparent ink window
- password input types are rejected before a connectionless handwriting session starts
- connectionless recognition is idle-delayed so multi-stroke and short multi-word input can complete before delivery
- newer strokes cancel a pending connectionless finish operation
- regular handwriting remains independent and still commits directly through InputConnection
- first native handwriting edit gesture implemented: horizontal scratch-out / zigzag deletion
- scratch-out recognition uses screen-coordinate stroke geometry, direction reversals, aspect ratio and path-length checks
- scratch-out is translated into Android's official DeleteGesture rather than directly editing text
- KeySwiper checks the current EditorInfo gesture capability before consuming the scratch stroke
- unsupported editors keep the stroke as ordinary handwriting instead of losing user input
- delete gesture uses word granularity and the editor decides which text intersects the gesture rectangle
- handwriting ink storage was refactored to retain individual strokes so a recognized edit gesture can discard only its own ink
- new unit tests cover scratch-out detection and rejection of normal handwriting strokes
- version advanced to 0.10.0-alpha10 / build 10

## 0.9.0-alpha09 — 2026-09-28

Direct Android stylus handwriting + KeySwiper app identity:

- KeySwiper now declares native Android stylus-handwriting IME support
- Android system handwriting lifecycle implemented through InputMethodService onPrepareStylusHandwriting, onStartStylusHandwriting, onStylusHandwritingMotionEvent and onFinishStylusHandwriting
- direct handwriting uses the system stylus ink window instead of requiring the in-keyboard handwriting panel
- stylus strokes are rendered in a transparent KeySwiper ink overlay above the target app
- Google ML Kit Digital Ink recognizes stroke batches locally after a short idle debounce
- recognized handwriting is committed directly into the active target app through InputConnection
- handwriting model preparation begins in the Android handwriting prepare phase to reduce first-stroke latency
- Android 14+ handwriting session timeout is explicitly configured for continued multi-word writing
- eraser-tool input clears the current uncommitted ink batch
- new handwriting batches can arrive while a previous recognition request is running without blocking normal keyboard input
- direct handwriting feeds local prediction learning and personal vocabulary after successful recognition
- sensitive/password/no-learning fields refuse direct handwriting sessions
- existing in-keyboard handwriting pad remains available as a manual fallback
- KeySwiper InputMethod XML now advertises android:supportsStylusHandwriting=true
- official K swipe logo integrated as launcher/IME icon using a scalable neon vector resource
- Android application and IME service now publish the KeySwiper launcher icon
- CI releases now additionally publish a stable KeySwiper-latest.apk asset
- README can permanently link to releases/latest/download/KeySwiper-latest.apk without version-specific edits
- unit tests added for handwriting commit formatting
- version advanced to 0.9.0-alpha09 / build 9

## 0.8.0-alpha08 — 2026-09-27

Configurable S Pen / stylus actions:

- primary stylus button single-click is now independently configurable
- primary stylus button double-click recognition added with a 280 ms discrimination window
- secondary stylus button is independently configurable
- duplicate MotionEvent button reports are debounced before gesture interpretation
- configurable actions: Voice2Text, accept top prediction, next/previous swipe candidate, translate selection, clipboard, emoji, handwriting, undo last swipe, Settings or None
- default mappings remain familiar: primary single = Voice2Text, primary double = translate selection, secondary = next swipe candidate
- swipe candidate cycling through stylus buttons no longer trains intermediate candidates as corrections
- both primary and secondary stylus buttons work on the handwriting pad
- prediction state is retained so a stylus mapping can accept the current top word/sentence prediction
- content-affecting stylus actions are blocked in sensitive/password fields
- Settings UI added for saving and resetting stylus mappings
- Samsung Air Actions documented as an optional foreground/device-specific adapter rather than a required system-IME dependency
- new unit tests cover single/double-click interpretation and stale single-click consumption
- version advanced to 0.8.0-alpha08 / build 8

## 0.7.0-alpha07 — 2026-09-27

Multilingual language lanes, larger dictionaries and personal vocabulary:

- embedded German, English, Italian, French and Spanish core language packs expanded substantially
- prediction no longer assumes one active language for the entire sentence
- new code-switch resolver keeps up to three language lanes active in parallel
- language-lane scoring combines Google ML Kit hints, recent token vocabulary, token recency, distinctive diacritics, partial-word prefix matches and technical vocabulary
- mixed-language technical writing deliberately keeps German and English lanes alive together
- instant word completion ranks candidates across all active language lanes
- next-word prediction uses language-pack transition maps weighted by lane confidence
- sentence prediction uses the same code-switch lanes as instant prediction
- swipe decoding now draws its lexicon from the shared multilingual language packs
- swipe language ranking uses the active code-switch lanes instead of raw language hints alone
- accented and umlaut words are converted to an internal swipe form for geometry while committing the correctly spelled original word
- local personal vocabulary learns confirmed typed words and can influence completions and next-word ranking
- manually remembered words can be pinned from Settings with an optional language tag
- Settings can forget individual personal words or reset the complete personal vocabulary
- explicit swipe corrections pin the corrected word into the local vocabulary
- prediction selections feed chosen words into the personal vocabulary
- personal vocabulary remains local and is never updated in sensitive/password fields
- new tests cover German/English mixed-language lanes, distinctive-character language promotion, shared technical vocabulary and mixed-language prediction
- version advanced to 0.7.0-alpha07 / build 7

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
- external Kotlin Gradle Plugin 2.4.10 enabled because LiteRT-LM 0.17.1 ships Kotlin 2.4 metadata while AGP 9.4 built-in Kotlin remains 2.2.10
- Gradle Kotlin DSL migrated to modern `kotlin.compilerOptions` while the temporary AGP legacy Android DSL is explicitly suppression-scoped for external KGP compatibility
- LiteRT-LM `Message` responses are consumed through their text-rendering `toString()`/Contents representation in the 0.17.1 API
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
