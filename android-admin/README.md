# CBMS Admin Android App

This is the Android admin application for the CBMS project.

## Local setup

1. Open this `android-admin` folder in Android Studio.
2. Copy `local.properties.example` to `local.properties`.
3. Set the Supabase project URL and anon public key in `local.properties`.
4. Add your Firebase project's `google-services.json` to the `app` folder.
5. Sync Gradle, then run the app.

Do not commit `local.properties`, signing credentials, or private service-role keys.

## Build and test

From this folder, run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Inventory safety

Inventory adjustments are checked against quantities reserved by open orders. The app previews the resulting physical stock and prevents reductions or Set values that would fall below reserved stock. If Restock is rejected despite the displayed physical stock already exceeding reserved stock, refresh the inventory and investigate the live database row before changing the reservation rule.

## Cancelled orders

Cancelled orders are closed. Cash collection is hidden for cancelled orders, and the order detail dialog uses the latest refreshed order state rather than relying only on the initially selected snapshot.
