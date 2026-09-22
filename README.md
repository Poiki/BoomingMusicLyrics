<div align="center">

<img src="metadata/en-US/images/icon.png" width="160" height="160" alt="Booming Music icon">

# 🎵 Booming Music Lyrics

### Android Auto artwork, a native up-next list, and synchronized lyrics

**An unofficial fork of [mardous/BoomingMusic](https://github.com/mardous/BoomingMusic).**

This repository contains fork-specific changes. It is not an official Booming Music release and is not maintained by the upstream project.

[![Upstream Release](https://img.shields.io/github/v/release/mardous/BoomingMusic?style=for-the-badge&label=Upstream%20Release&logo=github)](https://github.com/mardous/BoomingMusic/releases/latest)
[![Upstream F-Droid version](https://img.shields.io/f-droid/v/com.mardous.booming?style=for-the-badge&label=Upstream%20F-Droid&logo=fdroid)](https://f-droid.org/packages/com.mardous.booming/)
[![Upstream Downloads](https://img.shields.io/github/downloads/mardous/BoomingMusic/total?style=for-the-badge&logo=github&label=Upstream%20Downloads)](https://github.com/mardous/BoomingMusic/releases)
[![License: GPL v3](https://img.shields.io/github/license/mardous/BoomingMusic?style=for-the-badge&color=orange&label=License&logo=gnu)](LICENSE.txt)
[![Contributor Covenant](https://img.shields.io/badge/Contributor_Covenant-2.1-4baaaa.svg?style=for-the-badge&logo=contributorcovenant)](CODE_OF_CONDUCT.md)
[![Upstream Telegram Channel](https://img.shields.io/badge/Telegram-Upstream_Chat-blue?style=for-the-badge&logo=telegram)](https://t.me/mardousdev)

</div>

## 🗂️ Table of Contents

- [What this fork changes](#what-this-fork-changes)
- [Car controls](#car-controls)
- [✨ Features inherited from Booming Music](#-features-inherited-from-booming-music)
- [📸 Screenshots](#-screenshots)
- [📥 Download & Install](#-download--install)
- [💻 Tech Stack](#-tech-stack)
- [🧩 Roadmap](#-roadmap)
- [🔗 Useful Links](#-useful-links)
- [🤝 Contributing](#-contributing)
- [💖 Support Development](#-support-development)
- [🙌 Credits](#-credits)
- [⚖️ License](#-license)

## What this fork changes

The changes below are relative to the upstream base, [commit `da051803`](https://github.com/mardous/BoomingMusic/commit/da051803a3046b82ba7b633ed7f905240dcb8c5a). Booming Music already provides Android Auto integration, synchronized lyrics, and the player features listed further below.

| Area | Changes in this fork |
|:-----|:---------------------|
| Car artwork loading | Bounded caches share decoded covers and encoded bytes between the media session and the artwork provider. Playback covers are resized to at most 512 pixels per side, and nearby covers are prefetched in playback order from local or cached artwork. Revisioned URIs prevent reusing outdated covers. |
| Lyrics in car artwork | A dedicated artwork mode shows the current and next synchronized lyric lines. Cards are rendered in the background, updates follow lyric timestamps, and a neutral placeholder prevents the normal album cover from flashing while a card is prepared. |
| Native up-next browser | A compact **Next** tab shows the current song first, followed by upcoming songs with their own covers under **Up Next**. The current row has a stable ID, unchanged rows are reused, and duplicate updates are suppressed. Selecting a row seeks to that queue entry without replacing the playlist or shuffle order. Lists show at most 40 songs per screen with a **More songs** folder for the rest. |
| Song totals | The phone's Songs title and the car's Songs section display the library total, respecting library filters. The phone reuses its loaded list; car category counts query IDs without loading every song's metadata. |
| Audio output | High-precision PCM output is the default when no sound processing is configured, while explicit output preferences and existing sound processing settings are respected. The saved output choice is loaded before constructing the sink. Neutral balance avoids per-sample calculations, and optional hardware offload requires gapless support plus speed/pitch support only when needed. |
| Stable song metadata | Lyrics stay inside the artwork and never replace the song title, display title, subtitle, or artist sent through the media session. These fields remain intact when switching artwork modes. |
| Shared lyrics state | The phone and car use a shared coordinator with cancellation on track changes, bounded caching, and separate loading, missing, instrumental, and error states. Late results from a previous song cannot replace the current presentation. |
| Text encoding | Common UTF-8/Latin-1/Windows-1252 mix-ups are repaired when reading lyrics, song titles, artists, and albums, including saved playlists and history. Correct Unicode text is preserved, and metadata searches include legacy encoded names. Lyrics files recognize Unicode byte order marks and prefer valid UTF-8 before automatic charset detection. |
| Artist credits | Collaborations appear under each credited participant instead of creating a separate artist for every combination. Names are grouped across differences in case, spacing, and Unicode composition. Stable artist IDs and a shared catalog cache serve phone and car browsing, artist details, artwork, and search. Shared songs are deduplicated when selecting several artists. |
| Lyrics reliability | Provider failures are distinguished from missing lyrics, instrumental results are preserved, empty LRCLIB responses are handled, file encoding detection is bounded, and the preferred lyrics file format setting uses the correct values. |
| Playback work | Unnecessary queue scans are skipped when sequential queue handling is disabled. Car button layouts are published only when they change. |

These changes focus on responsiveness and avoiding unnecessary background work. The car host controls artwork size, layout, and button placement; behavior has been checked in an Android Automotive emulator and still needs validation on individual vehicles.

Text repair runs when content is loaded and is shared by the phone and car. For example, `jabÃ³n` becomes `jabón`; complete encoded sequences can also recover punctuation and emoji without re-encoding unaffected text. The app does not rename music files or rewrite their tags automatically. File charset detection remains bounded to 64 KiB, and the explicit **Treat all lyrics files as UTF-8** preference is retained. Text already replaced with missing-character symbols cannot be reconstructed reliably.

The Artists view includes every participant by default. For example, tracks credited to `SFDK con Natos y Waor` appear under both **SFDK** and **Natos y Waor**, with the duo's name kept intact. Credits recognize `feat.`, `ft.`, `featuring`, `with`, `con`, `vs`, `x`, semicolons, and multi-value separators. Commas, slashes, ampersands, and plus signs are split only when a participant is independently present in the library, to avoid guessing at ambiguous band names. Original song credits remain visible during playback. **Show album artists** remains an optional alternative, and an explicitly saved choice is preserved.

## Car controls

Open **Next** in the car's media library to browse the playback queue. The short tab label avoids truncation on narrow car screens. The current song appears first with **Now playing** beside the artist; upcoming songs appear under the full-width **Up Next** heading. Tap an upcoming song to play it. The list follows the active shuffle and repeat settings, including showing only the current song under repeat-one. Selecting the current song resumes it without restarting it. Duplicate songs are treated as separate queue entries.

When the host limits the number of tabs, **Library** keeps all the original categories accessible. On hosts allowing only one tab, **Next** is inside **Library**. Layout, tab widths, and transition animations are rendered by Android Auto or Android Automotive.

The lyrics artwork mode is enabled in the **`github` build flavor** for Android Auto and Android Automotive controllers. The `fdroid` and `playstore` flavors do not enable this extra control.

- **Lyrics cover on:** display lyrics inside the artwork area. Press the button again to restore the normal cover.
- The selected lyrics mode is restored when the car reconnects.

The old queue preview inside album artwork has been removed. Use the native **Next** library section to browse and select tracks. Depending on the host, the lyrics button may appear in the overflow menu.

For synchronized lyrics, the artwork shows the active line and the following line when available. Plain lyrics, instrumental tracks, missing lyrics, and loading failures have their own status cards. Song text metadata remains unchanged in every mode.

### Audio precision

The existing **High-Precision Audio Output** setting requests floating-point PCM from supported platform decoders and preserves high-resolution samples through the app's output path. Explicitly configured balance, ReplayGain, silence skipping, tempo, or equalizer settings retain the normal processing path unless high precision is explicitly selected. Output-mode changes require restarting the app. [Media3's floating-point output path](https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java) bypasses its app audio-processing chain.

FLAC playback has been checked with 16-bit/48 kHz and 24-bit/96 kHz files in an Android Automotive emulator: the app's active AudioTrack used floating-point PCM at the respective input sample rates. This does not guarantee bit-perfect output from a physical OnePlus, Bluetooth connection, or car. Android's mixer and the connected output device can resample or encode the signal. Hardware offload remains optional and depends on the device's reported capabilities.

## ✨ Features inherited from Booming Music

The following features and phone screenshots come from the original Booming Music project.

- 🎼 **Automatic Lyrics Download & Editing** – Automatically fetch, sync, and edit lyrics with ease.
- 💬 **Word-by-Word Synced Lyrics** – Enjoy immersive real-time lyric playback with word-level timing.
- 🌍 **Translated Lyrics Support** – Display dual-language lyrics via TTML or LRC with translations.
- 🔊 **Built-in Equalizer** – Powerful EQ with up to 15 fully configurable bands and customizable profiles.
- 🎧 **AutoEq Support** – Import professionally tuned headphone correction profiles for the most accurate sound possible.
- 🔄 **Gapless Playback** – Smooth transitions between songs with zero interruption.
- 🧠 **Smart Playlists** – Auto-generated lists like *Recently Played*, *Most Played*, and *History*.
- 📈 **Native Scrobbling** – Seamlessly sync your listening history with **Last.fm** and **ListenBrainz**.
- 🎧 **Bluetooth & Headset Controls** – Manage playback easily via connected devices.
- 🚗 **Android Auto Integration** – Full hands-free experience on the road.
- 🎨 **Material You Design** – Dynamic theming for a modern and personal interface.
- 📂 **Folder Browsing** – Play songs directly from any folder.
- ⏰ **Sleep Timer** – Automatically stop playback after a set time.
- 🧩 **Widgets** – Lock screen and home screen controls for quick access.
- 🔖 **Tag Editor** – Edit song metadata such as title, artist, and album info.
- 🔉 **ReplayGain Support** – Maintain consistent volume across all tracks.
- 🖼️ **Automatic Artist Images** – Download artist artwork for a polished library look.
- 🚫 **Library Filtering** – Easily exclude or include folders with blacklist/whitelist options.

## 📸 Screenshots

<div align="center">
<table>
<tr>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/1.jpg" alt="For You" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/2.jpg" alt="Songs" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/3.jpg" alt="Albums" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/4.jpg" alt="Album View" width="180"/></td>
</tr>
<tr>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/5.jpg" alt="Search" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/6.jpg" alt="Normal" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/7.jpg" alt="Full" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/8.jpg" alt="Gradient" width="180"/></td>
</tr>
<tr>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/9.jpg" alt="Plain" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/10.jpg" alt="M3" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/11.jpg" alt="Expressive" width="180"/></td>
<td align="center" width="25%"><img src="metadata/en-US/images/phoneScreenshots/12.jpg" alt="Peek" width="180"/></td>
</tr>
</table>
</div>

## 📥 Download & Install

### Build this fork

This repository currently provides the fork's source code. To build an optimized APK, use JDK 21 and an Android SDK installation matching the project's SDK requirements:

```sh
git clone https://github.com/Poiki/BoomingMusicLyrics.git
cd BoomingMusicLyrics
./gradlew :app:assembleGithubRelease
```

On Windows, use `gradlew.bat` instead of `./gradlew`. APKs are written to `app/build/outputs/apk/github/release/`:

- `*-github-arm64-v8a.apk`: ARM64 phones, including the OnePlus 15.
- `*-github-universal.apk`: all supported architectures in one larger APK.

The release app is named **Booming Music**, without the **Debug** suffix. It retains the upstream application ID, `com.mardous.booming`, and version numbering. Installing it over an official build requires a matching signing key. The debug variant uses a separate application ID and can remain installed alongside it.

Release signing uses the existing keystore configuration when supplied; otherwise, the project falls back to the local debug signing key. Configure a consistent signing key for builds you distribute or update.

**Updater:** the built-in updater still points to the original `mardous/BoomingMusic` repository. It does not provide updates for this fork. Keep using builds from this source tree to retain the fork-specific changes.

To run the regression tests:

```sh
./gradlew :app:testGithubDebugUnitTest
```

The current changes include 90 unit tests covering collaborative artist grouping, stable identity, catalog reuse and invalidation, text encoding repair, Unicode preservation, lyrics file decoding and word timing, saved metadata and search compatibility, lyrics resolution, stale result rejection, artwork caching, lyrics mode switching, bounded queue pages, stable queue-entry references, car tab limits, duplicate browser-update suppression, audio output preferences, and neutral balance sample preservation. Phone and Android Automotive emulator checks cover artist details and collaboration search. Automotive checks also cover metadata preservation, track changes, shuffle, repeat, live browser updates, song totals, and selecting browser rows without replacing the queue or shuffle order.

### Official upstream downloads

The links below distribute the original Booming Music application. They do **not** include this fork's additions.

<div align="center">

|                                                                                   Source                                                                                    | Details                                   |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:------------------------------------------|
|                               [<img src="assets/badge-playstore.png" alt="Play Store" height="40">](https://github.com/mardous/BoomingMusic)                                | Coming soon!                              |                                     |
|                      [<img src="assets/badge-github.png" alt="GitHub Releases" height="40">](https://github.com/mardous/BoomingMusic/releases/latest)                       | Direct APK download                       |
|                              [<img src="assets/badge-fdroid.png" alt="F-Droid" height="40">](https://f-droid.org/packages/com.mardous.booming)                              | Fully FOSS version                        |
|                       [<img src="assets/badge-izzyondroid.png" alt="IzzyOnDroid" height="40">](https://apt.izzysoft.de/packages/com.mardous.booming/)                       | Just like F-Droid but with faster updates |                         |
| [<img src="assets/badge-obtainium.png" alt="Obtainium" height="40">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/mardous/BoomingMusic/) | Automatic updates from GitHub             |
|                         [<img src="assets/badge-openapk.png" alt="OpenAPK" height="40">](https://www.openapk.net/boomingmusic/com.mardous.booming/)                         | Alternative APK source                    |

</div>

## 💻 Tech Stack

| Layer                   | Technology                                                      |
|:------------------------|:----------------------------------------------------------------|
| 🎧 Audio Engine         | [Media3 ExoPlayer](https://developer.android.com/media/media3)  |
| 🧱 Architecture         | MVVM + Repository Pattern                                       |
| 💾 Persistence          | Room Database + DataStore + SharedPreferences                   |
| ⚙️ Dependency Injection | [Koin](https://insert-koin.io/)                                 |
| 🧵 Async                | Kotlin Coroutines & Flow                                        |
| 🧩 UI                   | Android Views + Jetpack Compose (hybrid)                        |
| 🖼️ Image Loading       | [Coil 3](https://coil-kt.github.io/coil/)                       |
| 🎨 Design               | Material 3 / Material You                                       |
| 🗣️ Language            | Kotlin                                                          |

## 🧩 Roadmap

This is the upstream project's roadmap, retained for reference; it is not a delivery plan for this fork.

- [ ] 📦 Independent library scanner (no MediaStore dependency)
- [ ] 🎨 Multi-artist support (split & index properly)
- [ ] 🎵 Improved genre handling
- [ ] 🔁 Last.fm integration (import/export playback data)
- [ ] 💿 Enhanced artist pages (separate albums and singles visually)
- [ ] 🌐 Jellyfin & Navidrome integration

## 🔗 Useful Links

- **[This fork](https://github.com/Poiki/BoomingMusicLyrics)** — source code and fork-specific changes.
- **[Original Booming Music](https://github.com/mardous/BoomingMusic)** — official project and upstream development.

The documentation, community, and translation links below belong to the upstream project.

- 🔐 **[Requested Permissions](https://github.com/mardous/BoomingMusic/wiki/Advanced-Info#-permissions)**  
  What the app needs and why

- 🚘 **[Android Auto Setup](https://github.com/mardous/BoomingMusic/wiki/Advanced-Info#-android-auto-setup)**  
  How to enable and troubleshoot

- 🎧 **[Supported Formats](https://github.com/mardous/BoomingMusic/wiki/Advanced-Info#-supported-formats)**  
  Compatible audio formats

- 💬 **[Community](https://github.com/mardous/BoomingMusic/wiki/Community)**  
  Users and contributors

- 🌐 **[Translations](https://hosted.weblate.org/projects/booming-music/)**  
  Help us translate Booming Music into your language

- ❓ **[FAQ](https://github.com/mardous/BoomingMusic/wiki/FAQ)**  
  Common questions

## 🤝 Contributing

Booming Music is open-source — contributions are **always welcome!**
Check the [Contributing Guide](CONTRIBUTING.md) for details.

If you enjoy the app or want to support its development, give the repo a ⭐ — it really helps!
You can also:
- Open issues
- Submit pull requests
- Suggest new ideas

**Translations:** Managed on [Hosted Weblate](https://hosted.weblate.org/projects/booming-music/).

[![Translation Status](https://hosted.weblate.org/widget/booming-music/horizontal-auto.svg)](https://hosted.weblate.org/projects/booming-music/)

## 💖 Support Development

The support information and acknowledgments below are retained from the original project. These links support upstream Booming Music development.

Booming Music is an open-source project developed and maintained with passion in my spare time.
If you enjoy the app and the free features it offers, please consider supporting me to help cover
development costs and dedicate more time to new features.

Your support is greatly appreciated and keeps me motivated to continue improving Booming Music!

<div align="center">

<a href="https://ko-fi.com/christiaam" target="_blank">
<img src="https://storage.ko-fi.com/cdn/brandasset/v2/support_me_on_kofi_red.png" alt="Support me on Ko-fi" style="border: 0px; height: 40px;" />
</a>

### ❤️ Supporters

<table>
  <tr>
    <td>
      <b>mbeezy</b><br/>
      <b><a href="https://github.com/Qoojoe">KKTweex</a></b><br/>
      <b><a href="https://github.com/FabiRich">FabiRich</a></b><br/>
      <b><a href="https://github.com/Bloodaxe95">Bloodaxe</a></b><br/>
      <b>Bernhard</b><br/>
      <b>Andreas Hirth</b>
    </td>
    <td>
      <b>Revolver327</b><br/>
      <b>Peter Smith</b><br/>
      <b>Michele Simoncelli</b><br/>
      <b>Kristof Lengyel</b><br/>
      <b>Tarvos</b>
    </td>
  </tr>
</table>

</div>

## 🙌 Credits

Inspired by [Retro Music Player](https://github.com/RetroMusicPlayer/RetroMusicPlayer).
Also thanks to:

- [AMLV](https://github.com/dokar3/amlv)
- [LRCLib](https://lrclib.net/)
- [Better Lyrics](https://better-lyrics.boidu.dev/)
- [Lyrically API](https://lyrics.paxsenix.org/) (by [Alex](https://github.com/Paxsenix0))
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)

## ⚖️ License

```
GNU General Public License - Version 3

Copyright (C) 2025 Christians Martínez Alvarado

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

---

<p align="center"><a href="#readme">⬆️ Back to top</a></p>
