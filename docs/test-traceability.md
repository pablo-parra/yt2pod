# Test traceability

Non-functional requirements are tested as `NFR_*` (`JobServiceTest`, `ConversionControllerTest`): temp files deleted, no URL kept or echoed, job expiry, request cap.

Every test is named with the user story it verifies (`USxx_...` method names and `USxx` display names). Stories are covered as their milestone is implemented.

| Story | Status | Tests |
|---|---|---|
| US-01 Connect Drive | covered | `SetupModeApplicationTests.US01_*`, `SetupControllerTest.US01_*`, `DefaultGoogleOAuthClientTest.US01_*`, `GoogleDriveUploaderTest.US01_*`, `DriveHealthIndicatorTest.US01_*`, `Yt2podApplicationTests.US01_*` |
| US-02 Configurable Drive account | covered | `GoogleDriveGatewayTest.US02_*`, `DefaultGoogleOAuthClientTest.US01_US02_*` |
| US-03 Automatic token renewal | covered (against a fake Google) | `GoogleDriveGatewayTest.US03_*`, `GoogleDriveUploaderTest.US03_*`, `DriveHealthIndicatorTest.US03_*` |
| US-04 Web page | covered (page contents; browser behaviour checked manually) | `Yt2podApplicationTests.US04_*` |
| US-05 API / accepted link forms | covered | `ConversionControllerTest.US05_*`, `JobServiceTest.US05_*`, `YoutubeUrlValidatorTest.US05_*` |
| US-06 Link validation | covered | `YoutubeUrlValidatorTest.US06_*`, `YtDlpAudioExtractorTest.US06_*`, `ConversionControllerTest.US06_*`, `JobServiceTest.US06_*` |
| US-07 Audio only | covered | `YtDlpCommandBuilderTest.US07_*`, `YtDlpAudioExtractorTest.US07_*`, `JobServiceTest.US07_*` |
| US-08 Speech-oriented format | covered | `YtDlpCommandBuilderTest.US08_*`, `Yt2podApplicationTests.US08_US15_*` |
| US-09 Drive folder | covered (against a fake Google) | `GoogleDriveUploaderTest.US09_*`, `GoogleDriveGatewayTest.US09_*`, `JobServiceTest.US09_*` |
| US-10 Embedded metadata | covered (command flags) | `YtDlpCommandBuilderTest.US10_*` |
| US-11 Job status / failure reasons | covered | `JobServiceTest.US11_*`, `ConversionControllerTest.US11_*`, `YtDlpAudioExtractorTest.US11_*` |
| US-12 Resilience to YouTube changes | covered | `YtDlpCommandBuilderTest.US12_*`, `YtDlpAudioExtractorTest.US12_*`, `DefaultProcessRunnerTest.US12_*` |
| US-13 Playlists (could) | rejection covered | `YoutubeUrlValidatorTest.US13_*`, `YtDlpCommandBuilderTest.US13_*`; batch support not planned yet |
| US-14 Duration limit (could) | covered | `YtDlpCommandBuilderTest.US14_*`, `YtDlpAudioExtractorTest.US14_*`, `JobServiceTest.US14_*` |
| US-15 Self-host / env configuration | covered (config and concurrency); Docker image built and run manually | `Yt2podApplicationTests.US15_*` |
| US-16 Cloud (could) | n/a | — |

Verified manually (not automated): Docker image, real Google OAuth and Drive upload, real yt-dlp conversion, use from a phone over the LAN.

Not covered by automated tests: real calls to Google (the Drive/OAuth tests use a fake HTTP transport) and a real yt-dlp run against YouTube (needs network, yt-dlp, ffmpeg and Deno installed; to be done manually or as an opt-in test).
