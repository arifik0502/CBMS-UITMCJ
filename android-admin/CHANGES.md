# android-admin 0.4.0 — what changed

Apply the migration first, then build:

1. Run `supabase/migrations/20261119000003_admin_reporting_rpcs.sql` in the Supabase SQL editor (safe to re-run).
2. Add `developer.email=you@example.com` to `android-admin/local.properties` (see `local.properties.example`).
3. `.\gradlew.bat testDebugUnitTest assembleDebug`

## Bugs fixed
- Sales totals no longer silently stop at 500 orders: totals come from `get_sales_summary` (exact). Without the migration the app pages through up to 10,000 orders and flags the totals as approximate.
- Cash collections: server-side join (`get_cash_collections`), no more "1000 orders then join in the app". Fallback uses `id in (...)`.
- Dates are shown in Malaysia time (`Formats`). The order detail used the UTC date; the report's `Instant.parse` failed on `+00:00` timestamps and fell back to a raw string.
- A failed reload after a successful action no longer says "the action could not be completed".
- `CancellationException` is rethrown everywhere (`runCatchingCancellable`).
- Overview counts are whole-table (`get_order_counts`); "Delivered" is now "Delivered today".
- Notification de-dupe keeps real insertion order (new `recent_events_v2` pref; the old StringSet key is ignored).
- Realtime change counter replaced by `AdminDataBus` (no non-atomic `+= 1`).
- `AppUpdateControls`: fixed garbled "…" text and oversized spinners.

## Security
- Developer email is no longer in source. It comes from `local.properties` -> `BuildConfig.DEVELOPER_EMAIL` and is optional; the server OWNER check is still the real gate.
- Profile lookup is cached for 5 minutes (was one extra request on every call).
- Biometric / device-credential lock after 2 minutes in the background (skipped if the phone has no screen lock).
- Release builds: R8 + resource shrinking, serialization keep rules, signing config only when credentials exist.

## Performance / architecture
- Four ViewModels (`Orders`, `Catalog`, `Inventory`, `Report`) over an `AdminApi` interface; `AdminSupabase` implements it.
- Bottom-navigation tabs with `LazyColumn`; each tab refreshes only its own data (debounced realtime), plus pull-to-refresh.
- Buildings/trips cached for 10 minutes. Orders paginate (100 per page, "Load older orders", up to 1000).
- One `Money`, `Formats`, `OrderStatus` / `PaymentStatus` / `OrderRules` replace the duplicated formatters and magic strings.
- Removed dead `DeveloperHomeScreen.kt` (shipped as an empty stub; `cleanup.ps1` deletes it).

## Notifications
- Realtime and FCM no longer both notify (FCM is skipped while the live channel is connected).
- Text includes the order number; tapping opens that order. Proper notification icon; unique tag per event.
- Inventory alerts only fire when stock newly drops to 5 or fewer (needs replica identity FULL on `inventory`).

## UX
- Order detail is full-screen, shows the item lines (`order_items` + `products.name`; shows "unavailable" if the schema differs), and has an in-app pinch-zoom receipt viewer.
- Confirmation dialogs for verify payment, cash collected, and every status change.
- "Delivery plan" filter groups open orders by trip and building.
- Date pickers + presets (Today / Last 7 days / This month), Share CSV.
- Dark theme, status chips, Low-stock badge, login show-password / Done key / autofill.

## Tests / CI
- 9 new unit-test classes (money/dates, error mapping, order rules, filters, ranges, inventory, CSV, de-dupe, OrdersViewModel).
- `.github/workflows/android-admin.yml` runs `testDebugUnitTest assembleDebug` using placeholder config.

## Not changed on purpose
- `compileSdk/targetSdk 35`: Play's API 36 requirement needs a newer AGP + Gradle wrapper. Upgrade those first, then bump both.
- This was written without an Android SDK, so it has not been compiled. Send me the first build errors if any.
