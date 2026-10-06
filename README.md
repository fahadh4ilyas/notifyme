# notifyme

Mirror Android notifications from one phone to another over the internet. The
broadcasting phone is your main phone; the receiving phone shows a look-alike
notification (title, text, app icon, and a "From: &lt;app&gt;" subtitle). Notification
actions stay on the main phone — the receiver only shows the look.

Payloads are encrypted end-to-end (AES-256-GCM), so the relay in the middle
never sees the notification content.

## Components

- `android/` — a single Kotlin + Jetpack Compose app. Each device picks a role:
  **Send** (captures notifications) or **Receive** (re-posts them).
- `relay/` — a small self-hosted Go WebSocket relay. **Unused by the current
  app** (which uses Scaledrone instead); kept as a fallback if you ever want to
  self-host the transport.

## How it works

1. The sender's `NotificationListenerService` reads notifications (user grants
   *Notification access*), then encrypts each one with the shared E2EE key.
2. The encrypted payload is published to a room on **Scaledrone** (WebSocket).
3. The receiver subscribes to the same room, decrypts, and re-posts a static
   notification with the mirrored content.

The sender and receiver are paired by a **QR code** that carries the channel
ID, secret key, room code, and the E2EE key — so the receiver never types them.

Dismissal is **two-way**: swiping/clearing a mirror on the receiver dismisses
the original notification on the sender (and removing it on the sender removes
the mirror). Tapping a mirror just opens the app and does not reach back. The
WebSocket connection **auto-reconnects** after a few seconds if it drops.

## Prerequisites

1. Create a channel at [Scaledrone](https://www.scaledrone.com) and enable
   **"Always require authentication"**. Note its **Channel ID** and **Secret
   key** from the dashboard.
2. (Both phones) grant Notifyme **notification access** and **ignore battery
   optimization** when the app prompts.

## Build the Android app

Open `android/` in Android Studio and let Gradle sync, or from the CLI (the
wrapper and Gradle 8.9 are already set up):

```sh
cd android
./gradlew assembleDebug        # or assembleRelease
```

Install the APK from `app/build/outputs/apk/...` on **both** phones.

## Usage

1. On your **main phone**, open Notifyme:
   - Role: **Send**
   - Paste the **Scaledrone channel ID** and **secret key**.
   - Tap **Show QR** — this auto-generates the room code and E2EE key and
     displays the pairing QR.
   - Tap **Grant** for notification access, and **Choose apps to mirror** to
     exclude anything you don't want mirrored (all apps by default). Apps in
     Samsung **Secure Folder** (and other profiles) are listed by package name
     once they post a notification.
   - Tap **Start mirroring**.
2. On your **secondary phone**, open Notifyme:
   - Role: **Receive**
   - Tap **Scan QR** and point the camera at the main phone's QR. The channel
     ID, secret key, room code, and E2EE key fill in automatically.
   - Tap **Start mirroring**.

Keep both apps' foreground service running; notifications from the main phone
then appear on the secondary phone.

## Security

- **End-to-end encryption**: notifications are AES-256-GCM encrypted before
  publishing and decrypted only on the receiver. Scaledrone (and anyone who can
  read the channel) sees only ciphertext.
- The **Scaledrone secret key** is entered in the form and stored on-device; it
  is *not* compiled into the APK. However, it is the same credential on both
  devices and is also encoded in the QR, so treat the QR as sensitive.
- Scaledrone auth proves "I know the secret" and protects your quota — it does
  **not** hide content from anyone who also has the secret. The E2EE key is
  what keeps content private.

## Known platform limits

- The re-posted notification is always attributed to Notifyme by Android; it
  can only *visually mimic* the source app (title/text/icon + "From:" subtitle).
- Another app's small status-bar icon isn't accessible, so a generic bell icon
  is used; the large icon is copied from the source when available.
- Apps in Samsung **Secure Folder** (a separate Knox user profile) can't have
  their friendly name/icon resolved by a normal app, so they're shown by
  package name and appear in the filter list only after they post a
  notification.
- The `dataSync` foreground service has a 6-hour runtime limit on Android 15+.
