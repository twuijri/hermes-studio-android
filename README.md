# Hermes Studio for Android

An **unofficial, community-built** native Android client for
[Hermes Studio](https://github.com/EKKOLearnAI/hermes-studio).
Not affiliated with EKKOLearnAI.

The Studio web UI is built for the desktop, so this app talks to the same HTTP API
directly and renders a native, phone-shaped interface instead of wrapping a web view.

## What works today (v0.1)

- Sign in with your Studio server address, username and password
- Bearer token stored in `EncryptedSharedPreferences`, backed by the Android Keystore
- Browse profiles and pick which agent you talk to, with its current model shown
- Chat: send a message and get the full reply, with the conversation kept in one session
- Conversations list, tap one to continue it
- Start a fresh conversation at any time
- Dark theme, RTL-aware layout (Arabic reads correctly)

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
| Send a message | `POST /api/chat-run/runs` |

`POST /api/chat-run/runs` is the server's own REST wrapper around its Socket.IO chat
channel, which is what lets a mobile client get a complete answer without implementing
the streaming protocol.

All traffic goes to the address you enter, over HTTPS. Nothing is sent anywhere else,
there is no analytics, and the app has only the `INTERNET` permission.

## Roadmap

- Streaming replies (Socket.IO) instead of waiting for the final answer
- Group chat rooms
- Push notifications for finished runs
- Editing core settings (model, provider) from the phone
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
