# Code Switching and Language Packs

## Goal

KeySwiper does not force an entire message into one language.

A message such as:

`Ich habe den build deployed und teste ihn later.`

can keep German and English active at the same time. The same architecture applies to Italian, French and Spanish.

## Language lanes

The local resolver keeps up to three active language lanes. Each lane has a normalized confidence score.

Signals include:

- Google ML Kit possible-language hints from surrounding text;
- recent-token membership in KeySwiper language packs;
- recency of those tokens;
- distinctive characters and diacritics;
- the currently typed partial word;
- technical vocabulary shared across languages.

This is intentionally token-local rather than sentence-global. A language can gain or lose weight as the user changes language mid-sentence.

## Core packs

Version 0.7 ships larger embedded core packs for:

- German — `de`
- English — `en`
- Italian — `it`
- French — `fr`
- Spanish — `es`

Each pack contains:

- core vocabulary;
- technical vocabulary;
- common next-word transitions.

The pack registry is shared by normal prediction and swipe decoding so the two systems do not maintain contradictory dictionaries.

## Swipe and diacritics

The visible word remains correctly spelled, for example `möchte`.

For geometric swipe comparison KeySwiper generates an internal swipe form, for example `mochte`, because the current base QWERTY surface does not yet expose every accented character as its own key.

The internal form is used only for geometric matching. The committed candidate remains the original language-pack word.

## Personal vocabulary

Confirmed typed words are observed locally. Frequently used words become stronger completion candidates.

Users can also explicitly pin a word in Settings, optionally with a language tag. This is intended for:

- names;
- project names;
- technical terms;
- slang;
- uncommon vocabulary.

Pinned words receive a stronger ranking prior.

Personal vocabulary is stored locally, is bounded, and can be reset or edited by the user.

## Privacy

No language-lane or vocabulary learning runs in sensitive/password/no-personalized-learning fields.

The code-switch resolver operates only on the local context already available to the IME.
