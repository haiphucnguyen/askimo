#!/usr/bin/env bash
set -euo pipefail

# Ensure we run from the docs directory even if called from repo root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Friendly checks
if ! command -v bundle >/dev/null 2>&1; then
  echo "Bundler not found. Installing..."
  gem install bundler
fi

# Install gems (first run) and start local server with live reload
bundle install
JEKYLL_ENV=development bundle exec jekyll serve --livereload --host 127.0.0.1
