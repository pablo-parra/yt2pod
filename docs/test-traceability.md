# Test traceability

Every test is named with the user story it verifies (`USxx_...` method names and `USxx` display names). Stories are covered as their milestone is implemented.

| Story | Status | Tests |
|---|---|---|
| US-01 Connect Drive | pending (milestone 3) | — |
| US-02 Configurable Drive account | pending (milestone 3) | — |
| US-03 Automatic token renewal | pending (milestone 3) | — |
| US-04 Web page | pending (milestone 4) | — |
| US-05 API / accepted link forms | partial | `YoutubeUrlValidatorTest` (`US05_*`); endpoint pending (milestone 4) |
| US-06 Link validation | covered (validator level) | `YoutubeUrlValidatorTest` (`US06_*`), `YtDlpAudioExtractorTest.US06_*`; HTTP 400 mapping pending (milestone 4) |
| US-07 Audio only | covered | `YtDlpCommandBuilderTest.US07_*`, `YtDlpAudioExtractorTest.US07_*` |
| US-08 Speech-oriented format | covered | `YtDlpCommandBuilderTest.US08_*`, `Yt2podApplicationTests.US08_US15_*` |
| US-09 Drive folder | pending (milestone 3) | — |
| US-10 Embedded metadata | covered (command flags) | `YtDlpCommandBuilderTest.US10_*` |
| US-11 Job status / failure reasons | partial | `YtDlpAudioExtractorTest.US11_*` (failure reasons); job status pending (milestone 4) |
| US-12 Resilience to YouTube changes | covered | `YtDlpCommandBuilderTest.US12_*`, `YtDlpAudioExtractorTest.US12_*`, `DefaultProcessRunnerTest.US12_*` |
| US-13 Playlists (could) | rejection covered | `YoutubeUrlValidatorTest.US13_*`, `YtDlpCommandBuilderTest.US13_*`; batch support not planned yet |
| US-14 Duration limit (could) | covered | `YtDlpCommandBuilderTest.US14_*`, `YtDlpAudioExtractorTest.US14_*` |
| US-15 Self-host / env configuration | partial | `Yt2podApplicationTests.US15_*`; Docker image not yet verified |
| US-16 Cloud (could) | n/a | — |

Not covered by automated tests: a real yt-dlp run against YouTube (needs network, yt-dlp, ffmpeg and Deno installed; to be done manually or as an opt-in test).
