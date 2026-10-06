// Package main implements a minimal WebSocket relay for notifyme.
//
// Clients connect to /ws?room=<code>. Any message sent by a client is
// broadcast to every other client in the same room. There is no
// authentication or persistence; run it behind a firewall or a TLS
// reverse proxy (which also gives you wss://) for anything beyond
// personal use.
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"nhooyr.io/websocket"
)

type client struct {
	conn *websocket.Conn
	mu   sync.Mutex
}

func (c *client) writeText(ctx context.Context, msg []byte) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.conn.Write(ctx, websocket.MessageText, msg)
}

type hub struct {
	mu    sync.Mutex
	rooms map[string]map[*client]struct{}
}

func newHub() *hub {
	return &hub{rooms: make(map[string]map[*client]struct{})}
}

func (h *hub) join(room string, c *client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.rooms[room] == nil {
		h.rooms[room] = make(map[*client]struct{})
	}
	h.rooms[room][c] = struct{}{}
}

func (h *hub) leave(room string, c *client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	clients := h.rooms[room]
	delete(clients, c)
	if len(clients) == 0 {
		delete(h.rooms, room)
	}
}

// peers returns the other clients in the room.
func (h *hub) peers(room string, c *client) []*client {
	h.mu.Lock()
	defer h.mu.Unlock()
	out := make([]*client, 0, len(h.rooms[room]))
	for other := range h.rooms[room] {
		if other != c {
			out = append(out, other)
		}
	}
	return out
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	h := newHub()

	http.HandleFunc("/", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		_, _ = w.Write([]byte("notifyme relay\n"))
	})

	http.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		room := r.URL.Query().Get("room")
		if room == "" {
			http.Error(w, "missing room", http.StatusBadRequest)
			return
		}

		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
			// The Android client sends no Origin header.
			InsecureSkipVerify: true,
		})
		if err != nil {
			log.Printf("accept: %v", err)
			return
		}
		defer conn.Close(websocket.StatusInternalError, "connection closed")

		c := &client{conn: conn}
		h.join(room, c)
		defer h.leave(room, c)

		ctx := conn.CloseRead(r.Context())
		for {
			_, msg, err := conn.Read(ctx)
			if err != nil {
				return
			}
			for _, peer := range h.peers(room, c) {
				writeCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
				if err := peer.writeText(writeCtx, msg); err != nil {
					log.Printf("write: %v", err)
				}
				cancel()
			}
		}
	})

	addr := ":" + port
	log.Printf("notifyme relay listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}
