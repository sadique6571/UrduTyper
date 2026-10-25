# Firebase credential and GitHub Actions setup

The Android client configuration `presentation/google-services.json` is not tracked.

## Required repository secret

1. In Firebase Console, download the updated Android `google-services.json` for package `me.nomi.urdutyper`.
2. Encode the raw JSON file as **single-line base64**:
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\\path\\to\\google-services.json"))`
   - macOS: `base64 < google-services.json | tr -d '\\n'`
   - Linux: `base64 -w 0 google-services.json`
3. GitHub repo → Settings → Secrets and variables → Actions → New repository secret.
4. Name the secret `FIREBASE_GOOGLE_SERVICES_JSON_B64` and paste the encoded string. Never commit it.
5. Re-run the Android build workflow.

The Android build validates Firebase project `urdutyper2`, package name, both web and Android OAuth clients, and stable debug signing SHA-1.

## Cloud security

Firebase client API keys are not authorization secrets. Restrict to needed Firebase APIs and review Android application restrictions with Firebase Authentication in a test build, then enforce Firebase Security Rules, App Check and quotas. Rotate a previously published key if Google Cloud reports abuse or requires rotation. Removing a tracked file from the latest commit does **not** remove it from Git history.

The committed stable debug keystore is **not production signing material**. Configure a privately stored release keystore before distributing a production APK.
