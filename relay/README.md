# notifyme relay

Minimal WebSocket relay that forwards messages between the two devices in a
room. No auth, no database — a room is just whatever `?room=` code the clients
agree on.

## Run

Requires Go (the module targets Go 1.22+).

```sh
cd relay
go mod tidy
PORT=8080 go run .
```

Or build a static binary and run it anywhere:

```sh
go build -o notifyme-relay .
./notifyme-relay
```

## Client URL

The Android app expects a URL that it appends `/ws` to (unless it already ends
in `/ws`), e.g.:

```
ws://your-server:8080
```

For a secure connection, put the relay behind a reverse proxy that terminates
TLS (Caddy, nginx, Traefik) and use:

```
wss://your-domain.example.com
```

The relay accepts connections from any origin and does not authenticate
clients. If you expose it to the public internet, add auth at the proxy layer
or firewall it to your own devices.
