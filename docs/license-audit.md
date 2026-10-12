# Dependency and license audit

## Current AGPLv3 source

Anchor's original code and documentation now use **AGPL-3.0-only**. The prior MIT notice and standard GPLv3 text remain in `licenses/`; existing MIT and GPLv3 grants are not revoked. Android preview 5 and Windows 1.0.6 remain GPLv3 releases with their original corresponding-source archives.

The Samsung Health Data SDK AAR and Google Play Services authentication integration have been removed. Health habits use **AndroidX Health Connect 1.1.0 (Apache-2.0)**. Calendar occupancy uses the Android Calendar Provider, including Google calendars synced by the device's calendar app. No proprietary health or authentication SDK remains in the resolved runtime graph.

All 21 plant GLBs, the forest gallery, species catalog, plant renderers, SceneView/Filament dependencies and six unverified harbor image assets have been removed. Focus rewards are awarded once without creating forest records. Legacy backup/database fields remain for upgrade compatibility.

### Resolved runtime evidence

The Android debug/release and Windows runtime configurations resolve to **152 distinct Maven artifacts** (112 Android debug, 107 Android release and 51 Windows). All declare open-source terms, including licenses inherited from parent POMs. `licenses/dependency-inventory.json` records exact coordinates, platforms and declared licenses without local paths. These are declarations, not proof of every upstream contribution's provenance.

- AndroidX, Compose, Kotlin, coroutines, DataStore, OkHttp, Okio, Gson, Guava and PDFBox: Apache-2.0.
- Checker Framework annotations: MIT. Protocol Buffers: BSD-3-Clause. Their upstream notices are retained.
- JNA/JNA Platform 5.14.0: use the Apache-2.0 alternative permitted by [upstream](https://github.com/java-native-access/jna/blob/5.14.0/LICENSE).
- SQLite JDBC: Apache-2.0, with embedded SQLite public-domain and legacy wrapper notices retained.
- Android native llama.cpp b9371: MIT. Its source archive is pinned by SHA-256 in CMake. Retained notices include the upstream MIT and bundled native license files.
- Android NDK 27.0.12077973: separate native-runtime terms, including Apache-2.0 with LLVM exception. Both upstream NDK NOTICE files are included in the notice bundle. OpenCL remains disabled.
- Skiko 0.8.18: Apache-2.0; its pinned Skia build `m126-6bfb13368b` is BSD. Upstream notices for its packaged graphics dependencies are included from the matching Windows Skia archive.
- Microsoft OpenJDK 17.0.20.1+1-LTS: GPL-2.0 with Classpath exception. Windows retains the complete `runtime/legal` directory. The installed runtime's source identifier matches [commit 3a8c0bd1eefc84208d0d3abd89d01ef0a63a5002](https://github.com/microsoft/openjdk-jdk17u/tree/3a8c0bd1eefc84208d0d3abd89d01ef0a63a5002); the corresponding source archive accompanies the release.
- Android MiniCPM5 Q8_0 weights: the [exact GGUF publisher](https://huggingface.co/GnLOLot/MiniCPM5-1B-Claude-Opus-Fable5-Thinking-GGUF), fine-tuned model and OpenBMB base model declare Apache-2.0. The model is separately attributed and checksum-verified. This does not independently verify training-data provenance. Windows does not bundle weights or `llama-server`.

### Notices and source

Full license texts and retained upstream copyright/NOTICE material are packaged from `licenses/` into Android assets and Windows resources. Settings provides offline access to AGPLv3 and third-party notices. Windows also exposes AGPLv3 in installer packaging. The Android META-INF exclusions avoid collisions; their license material is preserved in the separate notice bundle.

Application source, native source and the matching Windows OpenJDK source are distributed beside binary release assets. `docs/development.md` records build commands, toolchain requirements and the model download/hash. Third-party code retains its own terms; changing Anchor's license does not relicense dependencies.

The reviewed permissive dependencies retain Apache-2.0, MIT, BSD or their stated runtime exceptions. AGPLv3 section 13 permits combination with GPLv3 code while preserving each license's terms; it adds corresponding-source obligations for modified versions supporting remote network interaction. See [AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html) and the [migration record](license-migration.md). Changing Anchor's license does not retroactively alter earlier releases or downloaded copies.

### Validation limits

Host builds and unit tests cover integration logic, local-day aggregation, missing/revoked permissions, failed reads and duplicate focus completion. Health Connect permission screens, data sharing from Samsung Health, device inference and Android Accessibility enforcement still require physical-device checks. Dependency metadata plus notice collection is not an independent legal opinion or guarantee of all upstream provenance.
