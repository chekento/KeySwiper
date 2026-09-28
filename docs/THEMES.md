# Keyboard Theme Architecture

KeySwiper themes are data profiles rather than separate keyboard implementations. Visual choice never changes swipe geometry, prediction behavior or privacy rules.

## Available themes

### Matrix Cyber · Default
Near-black green/cyan Matrix/techno surfaces with restrained neon-mint accents.

### OLED Obsidian
True-black base surfaces, cool violet highlights and very high OLED contrast.

### Neon Tokyo
Deep violet surfaces with magenta controls and cyan swipe traces.

### Aurora Glass
Cool blue/teal surfaces inspired by aurora light and translucent glass.

### Ember Copper
Warm dark brown/copper surfaces with orange-gold interaction accents.

### Kawaii Cyber
Softer rounded cyber styling with pink/cyan accents while retaining keyboard readability.

## Architecture

A `KeyboardThemeProfile` controls:

- root background;
- panel surfaces;
- normal/special/pressed key colors;
- accent and secondary accent;
- primary/secondary text;
- borders;
- swipe trace;
- key/panel corner radii.

The selected theme ID is stored locally in `Prefs`. `KeyboardThemes.all` is the only registry Settings needs, so additional profiles can be exposed without branching keyboard logic.

## Design invariants

Themes must not:

- change key hitboxes or swipe geometry;
- change prediction ranking;
- change touch/stylus gesture thresholds;
- weaken text contrast;
- add network behavior;
- change sensitive-field privacy behavior.
