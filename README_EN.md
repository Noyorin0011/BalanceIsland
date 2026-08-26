[简体中文](README.md)

# Balance Island — Status Bar Balance MVP

A native Android app that displays balances, budgets, or API-key status for multiple AI providers in a transparent text strip at the top of the status bar. It targets most phones and tablets running Android 8.0 or later and is not tied to a specific brand or device model.

> The project is still in testing, but official GitHub releases now consistently provide release-signed APKs. Debug APKs are intended only for development and are not guaranteed to install over an official build.

> **The signing conflict has been resolved:** starting with `0.7.5`, official releases use one persistent release certificate, and CI rejects debug certificates before publishing. If you installed an early `0.7.2-debug`, `0.7.3-debug`, old `0.7.4-debug`, or another debug-signed build, you still need to uninstall it once—which clears the app's settings—before installing the latest official APK. Future official releases can then be installed as normal upgrades without uninstalling again.

> GitHub Actions builds a Debug APK with JDK 17 and Android SDK 35 when code is pushed to `main`, a pull request is opened, the workflow is run manually, or a `v*` tag is pushed. Only an official `v*` tag that matches the current app version uses repository secrets to build and publish a signed Release APK. See `CODEX_TASK_SPEC.md` for subsequent testing steps.

See [`CHANGELOG.md`](CHANGELOG.md) for the full version history. The risks and data boundaries of the unofficial plan-usage reader are documented in [`EXPERIMENTAL_FEATURES.md`](EXPERIMENTAL_FEATURES.md).

## Implemented features

- A Kotlin and Jetpack Compose settings UI organized in a hidden-by-default navigation drawer. Open it with a left-edge gesture or by tapping the title.
- A bottom-right floating action button starts or stops the status-bar island and reflects its current running state.
- An optional experimental ChatGPT/Codex plan-usage area. Before signing in inside the app, the user must acknowledge the risks of session leakage and private-interface breakage. An HTTP proxy can be enabled specifically for this WebView, and users can separately opt into updates every five minutes while the experimental page remains open. Saved plan windows can join the island rotation as `remaining percentage / time until reset`, appear in the main status-bar preview, and trigger an optional notification when a later read confirms a new reset cycle.
- A single-line `TYPE_APPLICATION_OVERLAY` status-bar strip that does not depend on vendor-specific cards or private SystemUI APIs.
- Displays only configured API accounts. Supported providers: DeepSeek, OpenAI, OpenRouter, SiliconFlow, Kimi/Moonshot, Xiaomi MiMo, Anthropic, Google Gemini, and xAI/Grok.
- Multiple keys can be stored for one provider; each account is cached, refreshed, and rotated independently.
- Selectable display formats: balance only, `used today / balance`, or rotation between the two every five seconds.
- When a provider has only one key and no account note, brackets are hidden automatically to save status-bar width.
- Rotate all configured accounts, pin any configured provider, or create a custom group containing selected providers only. Entries change every five seconds.
- Tap the text strip to switch accounts immediately; long-press it to return to settings.
- Four position presets: top-left or top-right safe area, and top-left or top-right edge.
- Safe-area presets read Android DisplayCutout, RoundedCorner, and status-bar insets while preserving a generic minimum side margin.
- Text content width is adjustable from `72–320dp`. Provider icons stay fixed; account labels and balances that exceed the viewport scroll smoothly in a loop, while short text remains still.
- Five visual styles: transparent text, ultra-thin translucent background, automatic contrast outline, ultra-thin adaptive background, and dual-layer high-contrast text. The dual-layer style adds a thin opposite-color outline around the system-selected light or dark text. Both background styles are only about `2px` taller than their content in total.
- Each API key can use an independent refresh interval from `1–1440` minutes. Enter `0` to use the provider recommendation; OpenAI defaults to five minutes.
- HTTP 429 responses honor `Retry-After` and use exponential backoff up to 24 hours. The last valid balance remains visible while rate-limited, and the normal schedule resumes after a successful request.
- Query entry points are globally serialized and manual refreshes have a 30-second debounce. Adding an account tests only the new key, preventing duplicate concurrent requests from settings, the overlay service, and background work.
- OpenRouter and OpenAI use official daily usage data. DeepSeek, Moonshot, and SiliconFlow estimate “used today” from persisted balance deltas. If the process is killed, balance decreases that occurred while offline are incorporated after recovery.
- Independent low-balance warnings per account: orange at or below `warning threshold × 1.5`, and red with a notification when the threshold is crossed.
- Independent balance-drop notifications per account, defaulting to one alert for every decrease of `5` currency units.
- Optional abnormal-change alerts per account using absolute, percentage, or either threshold, with an independent notification cooldown.
- An optional manual display balance per account; leaving it empty continues to use the API value.
- Switching the provider being added clears the note, API key, and visibility state so credentials from the previous provider cannot be saved accidentally under the next one.
- Optional automatic hiding after a long period with no balance changes. A changed balance restores the strip and sends a silent notification with no banner or sound.
- DeepSeek calls `GET https://api.deepseek.com/user/balance`.
- OpenAI `sk-admin-` keys call `GET /v1/organization/costs` and, when available, `GET /v1/organization/spend_limit`. `sk-proj-` and ordinary keys are validated only through `GET /v1/models`; their balance uses the manual setting.
- Xiaomi MiMo ordinary pay-as-you-go `sk-` keys are validated with the official `GET /v1/models` endpoint and `api-key` header. The official console exposes balance and usage, but no public balance or billing endpoint is available to ordinary keys, so the app uses a manual balance. Token Plan `tp-` keys are not called by this app.
- API keys are encrypted separately per account with Android Keystore and AES-GCM. Settings display only the account note or last four characters.
- Pasted credentials are extracted automatically when they begin with `sk-`, `AQ.`, `AIza`, or `xai-`; preceding `Bearer` text, descriptions, quotes, and whitespace are removed. If validation still fails, a dialog shows the interface error and asks the user to check the key.
- Automatically migrates the single DeepSeek or OpenAI key saved by the previous version.
- The text-strip foreground service handles minute-level refreshes. When the service is not running, WorkManager provides a fallback at Android's permitted 15-minute periodic interval.
- A “Balance monitor” Quick Settings tile starts or stops the strip. Android 13 and later can request that the tile be added from inside the app.
- Optional “restart after being reclaimed” behavior combines `START_STICKY`, delayed recovery after task removal, boot-completed broadcasts, and app-upgrade broadcasts.
- Built-in Simplified Chinese, Traditional Chinese (Taiwan), English, Japanese, and Korean. The settings UI, text strip, and system notifications switch together, either following the system or using the in-app selection.

## Status-bar examples

```text
[DeepSeek icon] [Main account] ¥37.28
[OpenAI icon] [9K2M] $18.42 available
```

Icons use recognizable provider brand outlines. Sources and trademark notices are listed in `NOTICE_BRAND_ICONS.md`. When a note is empty and multiple accounts use the same provider, the brackets contain the key's last four characters automatically.

## What “balance” means

DeepSeek, OpenRouter, SiliconFlow, and Kimi/Moonshot display account balances returned or calculated by official interfaces. Moonshot automatically tries the domestic `api.moonshot.cn` CNY endpoint and the international `api.moonshot.ai` USD endpoint. OpenAI does not provide a universal public prepaid-balance endpoint, so the app displays:

```text
Remaining monthly budget = organization hard spend limit - monthly Costs
```

If the organization exposes no readable hard spend limit, only month-to-date spending is displayed. Querying OpenAI organization spending requires an Admin API key created by an Organization Owner. Settings keep this permission difference visible. When an `sk-proj-` key is detected, the app validates model access instead of incorrectly calling organization billing endpoints and prompts the user to enter a manual balance. Admin keys also retrieve the organization's cost for the current day.

Ordinary OpenAI Project, Xiaomi MiMo, Anthropic, Google Gemini, and xAI/Grok keys are currently validated only against official model endpoints. The app never presents token usage as a monetary balance.

Google Gemini supports both the new Google AI Studio `AQ.` Authorization Key and the legacy `AIza` Standard Key through native `GET https://generativelanguage.googleapis.com/v1beta/models` requests with the `x-goog-api-key` header. The free tier has request and token quotas but no monetary balance. Postpaid accounts expose accumulated charges but no fixed remaining amount; both can leave manual balance empty and display only that the key is valid. Prepaid users can copy `Available credit` from Google AI Studio's `Dashboard → Usage and Limits`; a Gemini key itself cannot read the Cloud Billing balance.

For providers that expose a balance but no daily billing endpoint, the app stores the day's earliest balance, latest balance, and recognized top-up increases locally, then estimates consumption from decreases. Statistics start only when enabled. Midnight recovery and long periods offline can make the figure approximate, so the UI explicitly labels it “estimated used today.”

## Building

Recommended environment: Android Studio, JDK 17, and Android SDK 35. See [`BUILDING.md`](BUILDING.md) for complete first-time setup, command-line builds, APK output locations, release signing, and GitHub Actions details.

1. Open this directory in Android Studio.
2. Wait for Gradle Sync to finish.
3. Select the `app` configuration and a real Android device.
4. Click Run, or execute:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

The default APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

1. Tap the title or swipe from the left edge to open the navigation drawer.
2. Under “Accounts & alerts,” choose a provider, optionally enter an account note, and paste the key. Enter `0` for the recommended refresh interval or choose `1–1440` minutes, then tap “Add & test.” Repeat to add more accounts.
3. On the same page, edit each account's refresh interval, warning threshold, balance-drop step, abnormal-change thresholds, cooldown, and optional manual balance.
4. Under “Background & language,” configure the scheduler check interval, automatic hiding, automatic recovery, Quick Settings tile, and interface language.
5. Under “Island display” → “Displayed accounts,” choose automatic rotation, a custom rotation group, a pinned provider, or “Pin ChatGPT/Codex plan usage,” then configure content, position, width, and text style. Automatic rotation includes all configured API accounts and saved plan usage. Custom groups can combine either source or contain plan usage only.
6. While dragging the text viewport width slider, both the preview and a running island redraw in one-dp increments in real time.
7. Tap the bottom-right button to start the island and grant overlay permission. Tap it again to stop.
8. If the strip overlaps system icons, adjust the horizontal and vertical sliders. Tap the island to switch accounts and long-press it to return to settings.
9. To try ChatGPT/Codex plan usage, open the warning-colored Experimental section from the drawer and acknowledge the risks. If the page times out, an HTTP proxy can be configured for this WebView; never copy or share cookies after a failure. Automatic updates and reset-cycle notifications are both off by default. A notification is sent only after the next manual or in-page automatic read confirms that the reset time advanced; this adds no background requests. After a successful read, return to “Island display” → “Displayed accounts” to arrange plan usage with API accounts and show the reset countdown.

## General device adaptation

The text strip uses public WindowInsets APIs to read status-bar height, display cutouts, and top corner radii. Safe-area presets avoid screen edges automatically; edge presets retain a small generic margin. Vertical offset is always measured from the physical top of the screen, so `0 dp` means the top edge. X/Y sliders remain available for immediate adjustment on different systems. The project contains no brand-, model-, or vendor-card SDK special cases.

## Languages

- Follows the system language by default.
- Settings can select Simplified Chinese, Traditional Chinese, English, Japanese, or Korean.
- Android resource directories are the default `values`, plus `values-b+zh+Hant+TW`, `values-en`, `values-ja`, and `values-ko`.
- Android 13 and later also declare support for the system App Languages screen; earlier Android versions use the in-app setting.
- To add a language, create matching resources in `app/src/main/res/values-language-code/strings.xml`.

## GitHub Actions

- Pushes to `main`, pull requests, manual workflow runs, and `v*` tags all trigger a Debug APK build.
- Builds use JDK 17, Gradle 8.11.1, and Android SDK 35, and validate all five language resource sets first.
- Only an official `v*` tag matching the current `versionName` reads `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` to create an additional release-signed APK. Pull requests, `main` pushes, and manual builds never access these secrets.
- APKs are available under `Artifacts` on the corresponding Actions run. Debug artifacts are retained for 14 days and signed Release artifacts for 30 days by default.
- After a matching official tag succeeds, the workflow creates or updates its Release—for example, `v0.9.0`. It uses `NOTE.md` as the preferred release notes, or extracts the matching section from `CHANGELOG.md` when that file is absent, then uploads the certificate-checked `BalanceIsland-v<version>.apk`. A matching tag ending in `-debug` publishes only a prerelease Debug APK.
- Starting with `v0.9.2`, official releases also upload `BalanceIsland-v<version>-source-minimal.zip`. It contains only `app/src/main`, Gradle project files, launcher scripts, and required license notices for that exact tag—never tests, task files, caches, or build outputs.
- Debug APKs are for testing only. Public releases and in-place upgrades should always use APKs signed by the same Release Keystore.
- The official-release signing conflict is resolved: the workflow inspects the certificate and rejects `CN=Android Debug`. An early debug-signed installation needs one uninstall migration; later official releases can upgrade continuously.

## Security notes

- API keys are never written to source code, printed to logs, or included in Android backups.
- Every key is encrypted with a non-exportable AES key held by Android Keystore.
- OpenAI Admin keys are highly privileged. This MVP is suitable for personal sideloading; a public deployment should use a self-hosted read-only backend and short-lived tokens.
- ChatGPT/Codex plan reading is an unofficial experimental feature that is off by default. A login session is as sensitive as a password; see [`EXPERIMENTAL_FEATURES.md`](EXPERIMENTAL_FEATURES.md) for complete risks and clearing behavior.

## Known limitations

- Status-bar icon layouts and background power policies differ by brand. First-time setup may require X/Y adjustment and manually allowing autostart or background activity.
- A standard Android overlay cannot read the pixels behind other apps. Dual-layer high-contrast text chooses its main color from the system theme and adds a thin opposite-color outline, covering cases where the current app theme is opposite to the system theme. An ultra-thin adaptive background is still recommended over complex dynamic content.
- “Force stop” in system settings prevents broadcasts and background recovery. The app cannot and does not bypass Android's force-stop semantics.
- WorkManager periodic tasks have a 15-minute minimum and may be delayed by power-saving policies.
- Minute-level scheduling depends on the text-strip foreground service continuing to run. Actual network intervals are determined jointly by per-account settings and automatic backoff.
- OpenAI Costs data can be delayed by billing processing, and Costs pagination is not yet implemented.
- ChatGPT/Codex plan reading depends on a private interface and System WebView. Old WebView versions, authentication changes, or network conditions can cause failures, require another sign-in, or trigger rate limits. The in-app proxy affects only this app's WebView, not other providers or the whole device. Automatic updates are not a background job and run only while the experimental page is visible.
- Brand icons identify API providers and do not imply endorsement. Trademark terms should be reviewed again before a broad public release.

## Official interfaces

- DeepSeek Balance: <https://api-docs.deepseek.com/api/get-user-balance/>
- OpenAI Costs: <https://developers.openai.com/api/reference/resources/admin/subresources/organization/subresources/usage/methods/costs/>
- OpenAI Spend Limit: <https://developers.openai.com/api/reference/resources/admin/subresources/organization/subresources/spend_limit/methods/retrieve/>
- OpenRouter Credits: <https://openrouter.ai/docs/api/api-reference/credits/get-remaining-credits>
- OpenRouter key daily usage: <https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key>
- Moonshot China balance: <https://platform.moonshot.cn/docs/api/balance>
- Moonshot international balance: <https://platform.moonshot.ai/docs/api/balance>
- SiliconFlow `/user/info` change notes: <https://docs.siliconflow.cn/cn/release-notes/overview>
- Anthropic Models: <https://docs.anthropic.com/en/api/models-list>
- Gemini Models: <https://ai.google.dev/api/models>
- Gemini API key types: <https://ai.google.dev/gemini-api/docs/api-key>
- Gemini API billing: <https://ai.google.dev/gemini-api/docs/billing>
- xAI API: <https://docs.x.ai/docs/overview>
- Xiaomi MiMo List Models: <https://mimo.mi.com/docs/zh-CN/api/model/list-models>
- Xiaomi MiMo API key types: <https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration>

## Roadmap (TODO)

- [x] **Automatic hiding and silent notification (`0.8.0`):** hide the status-bar strip after a long period without balance changes; restore it when a change occurs and post only a silent notification icon, with no banner.
- [x] **Abnormal API-key change alerts (`0.8.0`):** detect abnormal balance or usage changes per key using absolute and percentage thresholds with notification cooldowns.
- [ ] **Interface reliability:** HTTP 429 exponential backoff and `Retry-After` support were completed in `0.8.3`. Continue distinguishing network, timeout, permission, and server errors, and add OpenAI Costs pagination.
- [ ] **Account management and tests:** add “delete all credentials,” plus unit tests for the alert state machine and SecureKeyStore migration/deletion.
- [ ] **Display modes:** add pinning for a specific account, same-provider totals, font size, and background opacity. Custom provider groups and maximum text width were completed in `0.8.2`.

## AI-assisted development statement

Noyorin leads the project concept, requirements, and acceptance. GPT-5.6-sol and DeepSeek V4-Flash (DSV4F) jointly assisted with implementation. The project maintainer remains responsible for reviewing, testing, and publishing all AI-generated or AI-modified code.
