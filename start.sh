#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example. Add YOUTUBE_API_KEY there or save it from the web client/options page."
fi

echo "Building and starting Docker services..."
docker compose up --build -d

echo ""
echo "Services:"
echo "  Backend:          http://127.0.0.1:8000"
echo "  Web client:       http://127.0.0.1:5173"
echo "  Extension files:  http://127.0.0.1:5174"
echo ""
echo "For Brave/Chrome extension use: brave://extensions -> Load unpacked -> extension/"
