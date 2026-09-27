# Prediction Engine

## Goal

KeySwiper's prediction strip is a permanent intelligent layer between the toolbar and the keyboard.

It is designed for two latency classes:

1. **Instant local prediction** — must react on every character without network latency.
2. **High-intelligence refinement** — can later use a compatible local model or explicit cloud provider without blocking normal typing.

## v0.4 local prediction stack

The current engine combines:

- partial-word completion;
- next-word transitions;
- static multilingual language packs;
- Google ML Kit language identification as a context signal;
- local personal unigram / bigram / trigram counts;
- learned followers from the user's own confirmed text;
- curated sentence-continuation patterns;
- explicit suggestion-selection reinforcement.

The strip shows up to six ranked chips. Longer sentence continuations use an arrow prefix so they are visually distinguishable from single-word predictions.

## Dynamic modes

The same strip has two states:

- **Prediction mode:** word completion, next word and sentence continuation.
- **Swipe correction mode:** immediately after a swipe, the top swipe candidates temporarily occupy the strip.

Typing, spacing or selecting a prediction returns the strip to normal prediction mode.

## Personal learning

Prediction learning is stored locally. It learns short transitions instead of storing complete messages as one history document. The store is bounded and periodically prunes low-frequency entries.

Sensitive fields and Android no-personalized-learning fields do not expose surrounding text to the prediction engine and do not update personal prediction data.

## GenAI strategy

Google's ML Kit Prompt API is powered by Gemini Nano through AICore and is attractive for on-device generation. However, current AICore documentation restricts GenAI inference when an app is not the foreground application. An Android IME runs as an input method while another application owns the foreground, so KeySwiper does not make Gemini Nano a required always-on predictor.

Future high-intelligence providers should therefore be additive:

- a compact custom on-device language model delivered with LiteRT / model delivery;
- a device-specific provider where IME-compatible inference is verified;
- an explicit secure cloud provider through a gateway, never with a service-account key embedded in the APK.

The local prediction strip remains usable even when all GenAI providers are unavailable.
