# Hermes Studio for Android

An **unofficial, community-built** native Android client for
[Hermes Studio](https://github.com/EKKOLearnAI/hermes-studio).
Not affiliated with EKKOLearnAI.

The Studio web UI is built for the desktop, so this app talks to the same HTTP API
directly and renders a native, phone-shaped interface instead of wrapping a web view.

## What works today (v0.7)

- **The same profile pictures Studio shows**: an uploaded avatar, or the Multiavatar
  generated from the profile name — rendered on the device and cached, so a launch
  draws them from disk instead of pulling them again
- **Your Studio logo as the app mark**, fetched from your own server (`/logo.png`) and
  cached; swap it for any picture on your phone from Settings → Appearance
- **First-run walkthrough** explaining what the app is and that you supply the
  Hermes Studio server yourself
- **Splash while the stored session is verified** — the sign-in form only appears when
  you actually need to sign in
- **Settings**: server and account, the active profile's default model, a gateway
  restart, reasoning effort and sign out
- Sign in with your Studio server address, username and password
- Bearer token stored in `EncryptedSharedPreferences`, backed by the Android Keystore
- **Your existing Studio conversations**, with the same list shape as the web sidebar:
  title, timestamp, profile badge and model
- **Open any conversation and read its real history** pulled from the server, then keep
  talking in the same session
- **All profiles filter**, matching Studio's dropdown, or scope the list to one profile
- **Group chat tab**: rooms with agent and member counts, open a room to read its messages
- **Composer laid out like Studio's**: a full-width field with a `+` button and
  context chips underneath, and a single trailing button that is the microphone until
  you type, then becomes send
- **The `+` sheet** carries everything the conversation needs: Camera, Gallery and File
  tiles, plus the model and the reasoning effort — new controls become one more row
- **Change the model** per conversation, applied with `POST /api/hermes/sessions/{id}/model`
- **Change reasoning effort** (default, low, medium, high), sent as `reasoning_effort`
  on every run, the same field the web composer sets
- Attachments upload to your server and ride along with the message as proper
  content blocks
- **Voice**: record, then either transcribe into the composer through your Studio
  STT provider or send the take itself as audio
- Profiles screen to switch which agent a new chat talks to
- Start a fresh conversation at any time
- Studio's dark palette, RTL-aware layout (Arabic reads correctly)

## Install

Grab `hermes-studio-android.apk` from the
[latest build](../../releases/tag/latest-debug) and open it on your phone.

Android shows **"Play Protect hasn't seen an app from this developer before"** — that
appears for every app installed outside the Play Store. Choose **Install anyway**.

Every push to `main` rebuilds that release, so the link always points at the newest
build, and each build is signed with the same project key so it installs straight over
the previous version.

> Installed a build from before 2026-07-30? Uninstall the old app once, then install
> this one. Those builds were signed with a throwaway key that CI regenerated on every
> run, which is why Android refused to update them in place.

## How it talks to your server

| Purpose | Endpoint |
| --- | --- |
| Sign in | `POST /api/auth/login` |
| Verify a stored token | `GET /api/auth/me` |
| Profiles | `GET /api/hermes/profiles` |
| Conversations | `GET /api/hermes/sessions?profile=…` |
| Conversation history | `GET /api/hermes/sessions/conversations/{id}/messages` |
| Group chat rooms | `GET /api/hermes/group-chat/rooms` |
| Room detail and messages | `GET /api/hermes/group-chat/rooms/{id}` |
| Upload an attachment | `POST /upload?profile=…` |
| Transcribe a recording | `POST /api/hermes/stt/transcribe` |
| Available models | `GET /api/hermes/available-models?profile=…` |
| Set a conversation's model | `POST /api/hermes/sessions/{id}/model` |
| Profile default model | `GET /api/hermes/config` · `PUT /api/hermes/config/model` |
| Restart a profile's gateway | `POST /api/hermes/profiles/{name}/gateway/restart` |
| Send a message | `POST /api/chat-run/runs` |
| App mark | `GET /logo.png` (static, cached on the device) |

`POST /api/chat-run/runs` is the server's own REST wrapper around its Socket.IO chat
channel, which is what lets a mobile client get a complete answer without implementing
the streaming protocol.

All traffic goes to the address you enter, over HTTPS. Nothing is sent anywhere else and
there is no analytics. The app asks for `INTERNET`, plus `RECORD_AUDIO` and `CAMERA` only
at the moment you first use the microphone or the camera. Recordings and captures are
written to the app cache, uploaded, and deleted immediately.

## Roadmap

- Show the reasoning text a run returns, collapsed under each reply
- Editing a profile's avatar from the app, not only reading it
- Profile-level settings, not just per conversation
- Posting into group chat rooms, not just reading them
- Streaming replies (Socket.IO) instead of waiting for the final answer
- Push notifications for finished runs
- Kanban and scheduled jobs, read-only first

## Build locally

```bash
gradle assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 34). CI builds the same target on every
push, so a local SDK is optional.

## Contributing

Issues and pull requests are welcome — this is meant to be a community client. If you
want to port the same API layer to iOS, `HermesApi.kt` is a single self-contained file
that documents every call the app makes.

## Credits

Generated profile pictures come from the Multiavatar generator, ported to Kotlin so the
app and the web UI draw the same face for the same profile. Avatars by
[Multiavatar.com](https://multiavatar.com) — its license ships in
`app/src/main/assets/multiavatar-LICENSE.txt`.

Hermes Studio's own artwork is **not** bundled in this APK. The logo you see is read
from the server you connect to, which is also why replacing `logo.png` on that server
changes the mark here.

## License

MIT — see [LICENSE](LICENSE).
