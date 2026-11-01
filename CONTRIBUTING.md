# Contributing

This file covers what a contributor meets first: how to work on the app, the rules for a
change, themes, and translations. Deep reference material is in [`docs/`](docs/).

## Start here

| Question | File |
|---|---|
| How do I build and install it? | [docs/building.md](docs/building.md) |
| How does the app work? | [docs/architecture.md](docs/architecture.md) |
| Why do some PIDs read zero? | [docs/pid-filtering.md](docs/pid-filtering.md) |
| Why is the app not in Android Auto? | [docs/aa-install-gate.md](docs/aa-install-gate.md) |
| How does a release happen? | [docs/releasing.md](docs/releasing.md) |
| Agents working in this repo | [AGENTS.md](AGENTS.md) |

## How to make a change

1. Branch off `master`. Never commit to `master` directly.
2. Make the smallest change that solves the problem. Do not reformat unrelated code.
3. Push the branch. CI runs `./gradlew ktlintCheck`, `./gradlew testDebugUnitTest` and
   `./gradlew lintRelease`, and all three must pass. `./gradlew ktlintFormat` fixes the
   mechanical style violations before you push.
4. Open a **draft** pull request. A maintainer merges it.

A clear commit message helps the release notes. The version bump reads conventional commit
prefixes: `feat:` raises the minor version, `!` or `BREAKING CHANGE` raises the major
version, everything else raises the patch version.

Two rules that surprise people:

- Ask before you add a dependency.
- Do not add network, analytics or telemetry code. Crash reporting through `ch.acra` is the
  only component that sends data off the phone.

## AI-assisted contributions

AI-assisted changes are welcome under the same rules as any other: the change is small, the
tests and lint pass, and a human reviews it. The bar does not move because a model helped write
it. Say in the pull request that AI was involved when it helps the review; it is context, not a
disqualifier.

## Themes

Each app theme consists of:

- a needle / indicator
- a dial background
- a dial background without red warning marks ("empty")
- a stopwatch background
- an app background
- a needle color
- a theme name
- a mark-color

Google/Android Auto and VAG have strict guidelines for a screen that is visible while
driving. **Not looking at the road when you should is dangerous!** The design principles
are:

- Nothing should be distracting
- Nothing should be hard to see or read
- Nothing should be too bright, especially at night

### Needle

The needle image should always be in the 12 o'clock position.

![Needle image](https://github.com/jilleb/mqb-pm/raw/master/app/src/main/res/drawable/needle_skoda.png "Needle")

### Dial background (with red warning zone)

- Dial background with marks on it:
  If you use marks on the graphic, the start and end positions should be in the same
  locations as the example. `sv_markColor` should be transparent in this case.

- Dial without marks on it:
  The app generates marks in the color specified in `sv_markColor`.

Always make sure there is enough room for data in the center circle.

![Dial image](https://github.com/jilleb/mqb-pm/blob/master/app/src/main/res/drawable/dial_background_skoda.png "Dial background")

### Dial background empty

The "empty" dial background is identical to the other dial background, but it has no red
warning zone. Always make sure there is enough room for data in the center circle.

![Dial image](https://github.com/jilleb/mqb-pm/blob/master/app/src/main/res/drawable/dial_background_empty_skoda.png "Dial background empty")

### Stopwatch background

Basically the same as the dial background, but this one should have marks on it.

![Stopwatch image](https://github.com/jilleb/mqb-pm/blob/master/app/src/main/res/drawable/sw_background_skoda.png "Stopwatch background")

### App background

The wallpaper in the app. It should be 800x400 px. This image is visible throughout the
entire application, so make sure it is calm.

![Background image](https://github.com/jilleb/mqb-pm/blob/master/app/src/main/res/drawable/background_incar_skoda.png "App background")

### Colors

- Needle color (`themedNeedleColor`)
  Needle colors are used in high-visibility mode. Any color works, semi-transparent ones
  are the usual choice.

- Mark color (`sv_markColor`)
  Mark colors should be transparent when the dial background graphic has marks on it.
  Otherwise, keep them white or whatever suits your theme.

### Theme name

Pick whatever you like.

## License

Contributions land under GPL-3.0, the license of this repository.
