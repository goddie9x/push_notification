# BUILD-GUIDE — Build the NotifyPush Shortcut by hand (tap by tap)

This document is the **authoritative spec** for building the `NotifyPush`
Shortcut. The file [`NotifyPush.shortcut`](NotifyPush.shortcut) is a
machine-built copy of this spec (valid plist, checked with `plistlib`) but it
has **never been tested on a real iPhone** — there is no iPhone in this
environment. If the import fails or behaves oddly, build by hand from this
guide; if the hand build works and the import does not, keep the hand build.

> Convention: **Shortcut Input** = the shortcut's input variable. When run
> from an automation (iOS 27), that is the **Notification** variable the
> trigger passes in. When run by hand (Share Sheet / Home Screen), it is the
> shared text or clipboard.

## 0. What this Shortcut does — and does not do

- It forwards a notification **verbatim** to the NotifyPush iOS app. It does
  **not** parse the text: no amount, no balance, no direction, no bank logic.
  The JSON body carries the whole text as `body` and `rawText`.
- It **never** forwards anything that looks like a one-time / security code
  (step 3).
- iOS **cannot listen to other apps' notifications in the background**. No
  third-party app can — in Kotlin, Swift, or anything else. The only capture
  path on iOS is the iOS 27 Shortcuts automation in section 2 below, which
  fires when a notification arrives and hands it to this Shortcut. That is a
  hard platform limit, not a missing feature.

## 1. The actions, in order

Add actions with **+ Add Action** / search. Names in English are the names
shown on an English iPhone.

### 1. Comment — config reminder

- Action: **Comment**.
- Text: `NotifyPush — forwards a notification verbatim to the NotifyPush iOS app (URL scheme pushnotification://ingest). CONFIG: set the SourceApp Text action below to the source app's name, and optionally fill the fallback webhook Dictionary (url/headerName/headerValue) to POST directly instead of opening the app. One automation per source app (iOS 27+).`

### 2. Fallback webhook Dictionary — leave as-is unless you want direct POST

- Action: **Dictionary** (Scripting → Dictionary).
- Three keys (tap **Add new item** for each):
  - `url`: `NOT-CONFIGURED`
  - `headerName`: `X-Api-Key`
  - `headerValue`: `PASTE_YOUR_API_KEY_HERE`
- Action: **Set Variable** → `FallbackConfig`.

> Honest limit: this Dictionary holds **one** webhook. Shortcuts cannot read
> the destination list stored in the NotifyPush app, so the fallback can only
> ever post to this single URL. Leave `url` as `NOT-CONFIGURED` to use the
> app (primary path) and skip the fallback entirely. The fallback path also
> has no delivery confirmation — per-destination results only exist in the
> app's History screen.

### 3. Read the fallback URL out of the Dictionary

- Action: **Get Variable** → `FallbackConfig`.
- Action: **Get Dictionary Value** → Key: `url`.
- Action: **Set Variable** → `FallbackUrl`.

### 4. Input → variable RawText

- Action: **Get Variable** → **Shortcut Input**.
- Action: **Set Variable** → `RawText`.

### 5. OTP filter — one-time / security codes are NEVER forwarded

- Action: **Get Variable** → `RawText`.
- Action: **Match Text** (Text → Match Text).
  - Pattern (paste verbatim, **Case Sensitive: off**):
    `otp|verification code|security code|one-time passcode|one time passcode|do not share|mã otp|mã xác thực|mã xác nhận|mã bảo mật|không chia sẻ`
  - These are generic OTP phrases only — nothing app-specific.
- Action: **Set Variable** → `OtpMatch`.
- Action: **Get Variable** → `OtpMatch`.
- Action: **If** → condition **Has Any Value**.
  - Inside the If branch:
    - Action: **Show Notification** — Title: `NotifyPush`, Body:
      `OTP / security code — not forwarded.`
    - Action: **Stop Shortcut** (Scripting → Stop Shortcut).
  - Close the branch: **End If**.

### 6. Receive time — ISO-8601

- Action: **Current Date** (Date → Current Date).
- Action: **Format Date** → Format: **Custom** → Format String:
  `yyyy-MM-dd'T'HH:mm:ssZZZZZ`
  (produces e.g. `2026-11-25T10:08:00+07:00`).
- Action: **Set Variable** → `ReceivedAt`.

### 7. Source app name — one shortcut copy per source app

- Action: **Text** — content: `CHANGE-ME`.
- Action: **Set Variable** → `SourceApp`.

> Replace `CHANGE-ME` with the source app's display name (e.g. `Slack`).
> Duplicate this whole Shortcut for each source app and edit this one Text
> action in each copy. The automation trigger already picks one app (section
> 2), so this name is a label for the payload; it should match the app the
> automation watches.

### 8. Build the JSON body (Text)

- Action: **Text** — paste the template below, then replace each
  `[name]` with the matching magic variable (tap the spot →
  **Select Variable** → pick the variable):

```json
{
  "schema": "notify-push/v1",
  "sourceApp": "[SourceApp]",
  "bundleId": "",
  "title": "",
  "subtitle": "",
  "body": "[RawText]",
  "receivedAt": "[ReceivedAt]",
  "rawText": "[RawText]",
  "device": "ios"
}
```

- Action: **Set Variable** → `JsonBody`.

> Why are `bundleId` / `title` / `subtitle` empty? The iOS 27 automation
> passes a single opaque **Notification** variable; depending on the
> Shortcuts version you may or may not be able to split it into Title /
> Subtitle / Body / App. If your Shortcuts version lets you pull those
> fields out of the Notification variable, replace the empty strings (and
> `sourceApp`) with the real values — the app accepts all nine fields. The
> payload works with them empty because the app dispatches on `rawText`.

### 9. Fallback branch — direct POST (skipped unless step 2 was configured)

- Action: **Get Variable** → `FallbackUrl`.
- Action: **If** → **Is Not** → `NOT-CONFIGURED`.
  - Inside the If branch:
    - Action: **Get Variable** → `FallbackConfig`.
    - Action: **Get Dictionary Value** → Key: `headerName`.
    - Action: **Set Variable** → `FallbackHeaderName`.
    - Action: **Get Variable** → `FallbackConfig`.
    - Action: **Get Dictionary Value** → Key: `headerValue`.
    - Action: **Set Variable** → `FallbackHeaderValue`.
    - Action: **Get Contents of URL** (Web → Get Contents of URL).
      - URL: tap the field → **Select Variable** → `FallbackUrl`.
      - Method: **POST**.
      - Headers → **Add new header** (2 headers):
        - `Content-Type`: `application/json`
        - Header name: tap → **Select Variable** → `FallbackHeaderName`;
          value: tap → **Select Variable** → `FallbackHeaderValue`.
      - Request Body: **JSON** → tap the body field → **Select Variable** →
        `JsonBody`.
    - Action: **Show Notification** — Title: `NotifyPush`, Body:
      `Sent directly to the fallback webhook.`
    - Action: **Stop Shortcut** — the app never sees this notification.
  - Close the branch: **End If**.

> If Shortcuts refuses a variable as a header *name*, replace that header
> with literal text (name `X-Api-Key`, value your real key). The
> `Content-Type` header stays literal either way.

### 10. Primary path — hand off to the app via URL scheme

- Action: **Get Variable** → `JsonBody`.
- Action: **URL Encode** (Web → URL Encode) → Mode: **Encode**.
- Action: **Set Variable** → `EncodedPayload`.
- Action: **Text** — content: `pushnotification://ingest?payload=[EncodedPayload]`
  (tap after `payload=` → **Select Variable** → `EncodedPayload`).
- Action: **Set Variable** → `IngestUrl`.
- Action: **Get Variable** → `IngestUrl`.
- Action: **Open URLs** (Web → Open URLs). It opens the URL from its input.
- Action: **Show Notification** — Title: `NotifyPush`, Body:
  `Handed to the NotifyPush app.`

The app decodes the payload, runs the OTP filter again, dispatches it through
the shared dispatcher, records it in History, and shows a brief in-app
confirmation banner.

## 2. iOS 27 automation setup — one automation per source app

**Requirements:** iPhone 11 / SE (2nd gen) or later, **iOS 27 or later**.
The "When I receive a notification from [app]" trigger only exists from
iOS 27. On older iOS the Shortcut still works by hand (Share Sheet /
clipboard), but there is no automatic capture.

### 2.1 Turn on notifications for the source app — all three places

The automation only fires for notifications that are **actually displayed**.
Missing this is the #1 reason an automation "does nothing":

1. **Settings → Notifications → [source app]** → turn on
   **Allow Notifications**.
2. On the same screen, turn on **all three**: **Lock Screen**,
   **Notification Center**, and **Banners**.
   - Banners alone are **not enough**: the notification must reach
     Notification Center for the trigger to catch it.
3. Inside the source app itself, turn on its push notifications (each app
   names this differently). If the app only sends SMS and never a push
   notification, there is nothing for the automation to catch — enable push
   in the app first.

### 2.2 Create the automation

1. Open **Shortcuts** → **Automation** tab (bottom) → **+** →
   **Create Personal Automation**.
2. Scroll down, pick the trigger **"When I receive a notification from"**.
3. Pick **exactly 1 source app**. Leave any text filter empty — receive all
   of that app's notifications. The OTP filter already lives in the Shortcut;
   filtering more here only risks dropping notifications.
4. Pick **Run Immediately** (no confirmation). "Run After Confirmation"
   means tapping confirm on every single notification — that defeats the
   automation.
5. Pick the Shortcut: **`NotifyPush`** (the copy whose SourceApp matches
   this app, if you made per-app copies).
6. **Turn the automation ON** (toggle on the Automation screen). This is the
   most common trap: an imported or newly created automation can sit at
   **OFF** while looking finished — and then nothing ever runs.
7. Repeat steps 1–6 for each further source app. The Shortcut is shared;
   each automation is per-app.

### 2.3 Alternative handoff: the NotifyPush App Intent

Instead of the URL scheme (step 10 above), the Shortcut can call the app's
own intent action:

1. In the Shortcuts editor, search actions for **NotifyPush** → add
   **Ingest Notification**.
2. Pass the `JsonBody` variable into its **Payload JSON** parameter.
3. The intent saves the payload for the app, which dispatches it when it
   foregrounds. This needs the App Groups capability enabled in the Xcode
   project (`group.com.god.pushnotification`); without it the intent path
   silently does nothing and the URL-scheme path still works.
4. Either path is fine — do not add both to the same Shortcut, or a
   notification will be dispatched twice.

## 3. Checks after building

1. Run the Shortcut by hand (▶ in the editor) with a test text input, e.g.
   `Test from Slack: deploy finished`. The NotifyPush app should open and
   show a confirmation banner; History should list the payload with a
   per-destination result.
2. Send yourself a real notification from the source app. The automation
   should fire with no tap. If nothing happens: re-check 2.1 (all three
   notification places) and 2.2 step 6 (automation ON).
3. Send yourself a message containing `Your OTP is 123456`. The Shortcut
   should stop with "OTP / security code — not forwarded." and nothing
   should appear in the app's History.
4. If the app opens but shows "Could not read the notification payload":
   the URL-encode step (section 1 step 10) is the suspect — confirm the
   **URL Encode** action is set to **Encode** and sits directly before the
   Text action that builds the URL.

## 4. Cross-reference

- [`actions.json`](actions.json): the same 43 actions in machine-readable
  form — use it to check a hand build, not to build from.
- [`generate-shortcut.py`](generate-shortcut.py): regenerates
  [`NotifyPush.shortcut`](NotifyPush.shortcut) from this spec.
  `python3 generate-shortcut.py [--source-app NAME]`.
- `../iosApp/KMP-INTEGRATION.md`: how the app consumes the payload
  (URL scheme, App Intent, shared dispatcher, retry rules).
