#!/usr/bin/env bash
set -euo pipefail

# Always run from docs directory even if invoked elsewhere
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Helper: add a path segment to PATH if not already present
add_path() {
  local p="$1"
  if [[ -d "$p" && ":$PATH:" != *":$p:"* ]]; then
    export PATH="$p:$PATH"
  fi
}

# Ensure Ruby exists (prefer a non system-managed one)
if ! command -v ruby >/dev/null 2>&1; then
  echo "Ruby not found. Please install Ruby (e.g. via: brew install ruby OR rbenv)." >&2
  exit 1
fi

# Prefer user gem directory to avoid sudo prompts
USER_GEM_BIN_DIR="$(ruby -e 'print Gem.user_dir')/bin"
add_path "$USER_GEM_BIN_DIR"

# Install bundler locally if missing (never use sudo)
if ! command -v bundle >/dev/null 2>&1; then
  echo "Bundler not found. Installing to user gem directory (no sudo)..."
  gem install --user-install bundler >/dev/null
  add_path "$USER_GEM_BIN_DIR"
fi

# Force bundler to use a project-local vendor path (avoids system gem writes)
# This creates ./vendor/bundle and caches gems there.
export BUNDLE_PATH="$SCRIPT_DIR/vendor/bundle"
# Also set GEM_HOME so any implicit gem installs land in the vendor dir during this run
export GEM_HOME="$BUNDLE_PATH"
export GEM_SPEC_CACHE="$BUNDLE_PATH/specs"
mkdir -p "$BUNDLE_PATH" "$GEM_SPEC_CACHE"

# Configure local bundler settings (idempotent)
bundle config set --local path "$BUNDLE_PATH" >/dev/null || true
bundle config set --local clean 'true' >/dev/null || true
bundle config set --local cache_all 'true' >/dev/null || true
bundle config set --local without 'development:test' >/dev/null || true
# (adjust the --local without groups above if you actually need development gems; Jekyll usually is not grouped)

# Concurrency for install (portable across macOS/Linux)
CPU_CORES="$( (command -v sysctl >/dev/null && sysctl -n hw.ncpu) || (command -v nproc >/dev/null && nproc) || echo 4 )"

echo "Installing gems into $BUNDLE_PATH (cores: $CPU_CORES)..."
# Retry & quieten normal noise; remove --quiet if you want verbose resolution
bundle install --jobs "$CPU_CORES" --retry 3

# Ensure jekyll executable is available after install
if ! bundle exec which jekyll >/dev/null 2>&1; then
  echo "Jekyll not found in bundle. Check your Gemfile includes: gem 'jekyll'" >&2
  exit 1
fi

# Launch local server with live reload
echo "Starting Jekyll server with live reload..."
JEKYLL_ENV=development bundle exec jekyll serve --livereload --host 127.0.0.1
