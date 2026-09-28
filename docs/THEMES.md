# Keyboard Theme Architecture

KeySwiper themes are data profiles rather than separate keyboard implementations.

## Default: Matrix Cyber

The first production-facing profile uses:

- near-black background;
- dark green-black raised surfaces;
- restrained neon-mint accents;
- rounded 12dp keys;
- fine low-contrast borders;
- mint swipe trace;
- high-contrast off-white labels;
- compact control-rail toolbar.

The target is futuristic Matrix/Cyber/Techno without breaking Android readability conventions.

## Architecture

A KeyboardThemeProfile controls:

- root background;
- panel surfaces;
- normal/special/pressed key colors;
- accent and secondary accent;
- primary/secondary text;
- borders;
- swipe trace;
- key/panel corner radii.

The selected theme ID is stored locally in Prefs. New themes can therefore be added to KeyboardThemes.all and exposed in Settings without rewriting input behavior.

## Future theme candidates

Future releases can add visual profiles such as:

- Android Graphite;
- OLED Black;
- Neon Tokyo;
- Retro Terminal;
- Minimal Glass;
- Kawaii Cyber;
- Accessibility High Contrast.

Theme work must not change swipe geometry or prediction behavior.
