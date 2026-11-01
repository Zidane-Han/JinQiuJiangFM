README
======

# Escapepod - Podcast Player for Android
<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/images/icon.png" width="192" />

**Version 1.6.x ("Es ist einfach Rockmusik")**

Escapepod is a straightforward and lightweight app that takes a minimalistic approach to podcast listening, which may not appeal to everyone. The app consists of only two screens: a list of all podcasts and a list of episodes for a single podcast. Escapepod lacks a podcast discovery feature, offering only a simple search option. Additionally, it opens RSS podcast links when tapped in a web browser.

Escapepod is free and open source. It is published under the [MIT open source license](https://opensource.org/licenses/MIT). Want to help? Please check out the notes in [CONTRIBUTE.md](https://codeberg.org/y20k/escapepod/src/branch/master/CONTRIBUTE.md) first.

## Install Escapepod
You can install Escapepod via F-Froid.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/org.y20k.escapepod/)

## Verify APK
APKs published on Codeberg Releases are signed with the following certificate.

```
SHA-256: 61:05:14:5F:84:E5:CB:D6:97:F0:F6:E1:2C:97:77:04:2E:9B:2D:B6:67:A7:FF:0D:A4:12:F6:74:35:31:6D:D8
```

## Frequent Questions

### Does Escapepod have playback queue?
Escapepod has a simple up-next feature. Tap play on an episode while listening to another one. You will be be given the opportunity add it to the up-next slot.

### What are the default settings?
- Auto-update does not download files over cellular network
- Escapepod by default only keeps two episodes

### Escapepod keeps more than two episodes.
Escapepod tries to reduce the number of episodes it keeps. Here are the rules for that:

- Escapepod keeps the latest two episodes
- Episodes, that have been started, or that have been downloaded manually, are kept, too
- Episodes that have not been listened to in more than two weeks are removed.

### Does Escapepod support OMPL?
You can import a podcast list using the [OPML](https://en.wikipedia.org/wiki/OPML) exchange format using the respective option in the app's Settings. The current podcast collection can be exported via Settings, too. Additionally Escapepod keeps an up-to-date OPML file in the folder `/Android/data/org.y20k.escapepod/files/collection/`.

### Where do the podcast search results come from?
Escapepod searches by default the [Podcastindex.org](https://podcastindex.org/) online database or, if you toggle the respective setting, the [gpodder.net](https://gpodder.net/directory/) online database.

## Screenshots (v1.6)
[<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/01-escapepod-playback.png" width="240">](https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/01-escapepod-playback.png)
[<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/02-escapepod-playback-details.png" width="240">](https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/02-escapepod-playback-details.png)
[<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/03-escapepod-shownotes.png" width="240">](https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/03-escapepod-shownotes.png)

[<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/04-escapepod-older-episodes.png" width="240">](https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/04-escapepod-older-episodes.png)
[<img src="https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/05-escapepod-add-podcast.png" width="240">](https://codeberg.org/y20k/escapepod/raw/branch/master/metadata/en-US/phoneScreenshots/05-escapepod-add-podcast.png)
