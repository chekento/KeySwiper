# Neural Prediction Roadmap

KeySwiper now has a `NeuralPredictionBackend` interface but does not bundle a neural model in the APK.

## Why LiteRT-LM

Google AI Edge currently provides LiteRT-LM for Android with a Kotlin API and support for local language models, including hardware acceleration where available.

The intended architecture is:

```
Keyboard event
   ↓
Instant prediction (< interactive latency)
   ↓
Local semantic beam search
   ↓
Optional LiteRT-LM neural continuation / reranker
   ↓
Hybrid ranker
   ↓
Prediction strip
```

## Model delivery

Large models should be downloaded separately and versioned independently of KeySwiper APK releases. The model manager should verify:

- model hash;
- available storage;
- RAM class;
- accelerator support;
- model license;
- supported languages;
- expected first-token latency.

No model download should be mandatory for normal keyboard operation.

## Neural tasks

The first neural provider should focus on narrow keyboard tasks rather than open-ended chat:

- generate 3 short next-phrase candidates;
- rerank existing local candidates;
- infer punctuation;
- predict code-switching language;
- suggest a concise completion matching local tone.

This keeps latency and battery demand bounded.
