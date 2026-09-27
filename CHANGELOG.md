# Changelog

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
