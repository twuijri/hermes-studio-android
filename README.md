# Hermes Studio for Android

An **unofficial, community-built** native Android client for
[Hermes Studio](https://github.com/EKKOLearnAI/hermes-studio).
Not affiliated with EKKOLearnAI.

The Studio web UI is built for the desktop, so this app talks to the same HTTP API
directly and renders a native, phone-shaped interface instead of wrapping a web view.

## What works today (v0.3)

- Sign in with your Studio server address, username and password
- Bearer token stored in `EncryptedSharedPreferences`, backed by the Android Keystore
- **Your existing Studio conversations**, with the same list shape as the web sidebar:
  title, timestamp, profile badge and model
- **Open any conversation and read its real history** pulled from the server, then keep
  talking in the same session
- **All profiles filter**, matching Studio's dropdown, or scope the list to one profile
- **Group chat tab**: rooms with agent and member counts, open a room to read its messages
- **Attachments**: pick an image or any file, it uploads to your server and rides
  along with the message as a proper content block
- **Voice**: hold the mic to record, then either transcribe it into the composer
  through your Studio STT provider or send the recording itself as an attachment
- Profiles screen to switch which agent a new chat talks to
- Start a fresh conversation at any time
- Studio's dark palette, RTL-aware layout (Arabic reads correctly)

## Install

Grab `hermes-studio-android-debug.apk` from the
[latest debug release](../../releases/tag/latest-debug), open it on your phone and
allow installation from unknown sources when prompted.

Every push to `main` rebuilds that release, so the link always points at the newest build.

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
| Send a message | `POST /api/chat-run/runs` |

`POST /api/chat-run/runs` is the server's own REST wrapper around its Socket.IO chat
channel, which is what lets a mobile client get a complete answer without implementing
the streaming protocol.

All traffic goes to the address you enter, over HTTPS. Nothing is sent anywhere else and
there is no analytics. The app asks for `INTERNET`, plus `RECORD_AUDIO` only when you
first tap the microphone; recordings are written to the app cache, uploaded, and deleted
immediately.

## Roadmap

- Reasoning view per conversation
- Settings: change a profile's model and provider from the phone
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

## License

MIT — see [LICENSE](LICENSE).
