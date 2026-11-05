# Stage 1: Build the Go binary
FROM golang:1.23-alpine AS builder

WORKDIR /app

RUN apk add --no-cache git

COPY go.mod go.sum* ./
COPY . .

# Ensure dependencies are resolved and build static binary
RUN go mod tidy && CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/dangling-api ./cmd/api

# Stage 2: Minimal runtime image with wireguard tools
FROM alpine:3.20

WORKDIR /app

RUN apk add --no-cache ca-certificates wireguard-tools iptables iproute2 curl

COPY --from=builder /app/dangling-api /app/dangling-api

EXPOSE 8080

CMD ["/app/dangling-api"]
