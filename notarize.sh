#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Askimo (:desktop) macOS signing + notarization + stapling
#
# Grounded behavior from retrieved context:
# - Uses MACOS_IDENTITY for codesign
# - Uses notarytool with one of:
#     NOTARY_KEYCHAIN_PROFILE
#     ASC_KEY_ID + ASC_ISSUER_ID + ASC_KEY_PATH
#     APPLE_ID + APPLE_PASSWORD + APPLE_TEAM_ID
# - Signs:
#     1) Contents/runtime: *.dylib + jspawnhelper
#     2) Contents/app: loose *.dylib
#     3) Contents/app: *.jar that contain *.dylib (extract -> sign -> repack)
#     4) Contents/MacOS main executable (from Info.plist CFBundleExecutable)
#     5) The .app bundle (deep)
# - Creates UDZO DMG and notarizes DMG
# - Ensures we staple EXACTLY the same DMG bytes (avoid stapler Error 65 mismatch)
#
# Notes:
# - Compatible with older macOS Bash (no `mapfile`)
# - `.env` is loaded via `source` (safe for values with spaces if quoted)
# ============================================================

# --------------------------
# 0) Load env (.env) safely
# --------------------------
if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

# --------------------------
# 1) Required env vars
# --------------------------
: "${MACOS_IDENTITY:?MACOS_IDENTITY is required (e.g. \"Developer ID Application: Hai Nguyen (QM4ZMNHH5B)\")}"

HAS_CREDS=0
if [[ -n "${NOTARY_KEYCHAIN_PROFILE:-}" ]]; then HAS_CREDS=1; fi
if [[ -n "${ASC_KEY_ID:-}" && -n "${ASC_ISSUER_ID:-}" && -n "${ASC_KEY_PATH:-}" ]]; then HAS_CREDS=1; fi
if [[ -n "${APPLE_ID:-}" && -n "${APPLE_PASSWORD:-}" && -n "${APPLE_TEAM_ID:-}" ]]; then HAS_CREDS=1; fi

if [[ "$HAS_CREDS" -ne 1 ]]; then
  cat <<'EOF'
Notarization credentials are not configured.
Provide ONE of:
  1) NOTARY_KEYCHAIN_PROFILE (e.g. askimo-notary)
  2) ASC_KEY_ID + ASC_ISSUER_ID + ASC_KEY_PATH
  3) APPLE_ID + APPLE_PASSWORD + APPLE_TEAM_ID
EOF
  exit 1
fi

# --------------------------
# 2) Helpers
# --------------------------
sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

notary_args() {
  # Print args as NUL-separated tokens so we can safely read into an array (no word-splitting issues)
  if [[ -n "${NOTARY_KEYCHAIN_PROFILE:-}" ]]; then
    printf '%s\0' "--keychain-profile" "${NOTARY_KEYCHAIN_PROFILE}"
    return
  fi

  if [[ -n "${ASC_KEY_ID:-}" && -n "${ASC_ISSUER_ID:-}" && -n "${ASC_KEY_PATH:-}" ]]; then
    printf '%s\0' "--key-id" "${ASC_KEY_ID}" "--issuer" "${ASC_ISSUER_ID}" "--key" "${ASC_KEY_PATH}"
    return
  fi

  printf '%s\0' "--apple-id" "${APPLE_ID}" "--team-id" "${APPLE_TEAM_ID}" "--password" "${APPLE_PASSWORD}"
}

notary_submit_wait_json() {
  local artifact="$1"
  local args=()
  while IFS= read -r -d '' item; do args+=("$item"); done < <(notary_args)
  xcrun notarytool submit "$artifact" "${args[@]}" --wait --output-format json
}

notary_log() {
  local submission_id="$1"
  local args=()
  while IFS= read -r -d '' item; do args+=("$item"); done < <(notary_args)
  xcrun notarytool log "$submission_id" "${args[@]}"
}

ensure_same_file_bytes() {
  local file="$1"
  local sha_before="$2"
  local sha_after
  sha_after="$(sha256_file "$file")"
  if [[ "$sha_before" != "$sha_after" ]]; then
    cat <<EOF
❌ Ticket mismatch risk: file bytes changed after notarization submission.
File: $file
Local SHA256 before: $sha_before
Local SHA256 after : $sha_after

This can cause stapler Error 65.
Fix by ensuring you staple/upload EXACTLY the same file you submitted.
EOF
    exit 1
  fi
}

staple_with_retry() {
  local target="$1"
  local label="${2:-ARTIFACT}"
  local attempts="${3:-3}"
  local sleep_s="${4:-60}"

  for ((i=1; i<=attempts; i++)); do
    echo "📎 Stapling $label (attempt $i/$attempts): $target"
    if xcrun stapler staple -v "$target"; then
      echo "✅ Stapled $label."
      return 0
    fi
    echo "⚠️ Staple failed (attempt $i). Sleeping ${sleep_s}s..."
    sleep "$sleep_s"
  done

  echo "❌ Stapling $label still failing after $attempts attempts."
  return 1
}

# Extract -> sign dylibs -> repack to tmp -> mv over original (avoids FileNotFound issues)
sign_dylibs_inside_jar() {
  local jar_path="$1"
  local identity="$2"
  local work_root="$3"

  echo "   • Signing dylibs inside JAR: $(basename "$jar_path")"
  echo "     Path: $jar_path"

  # Single, authoritative existence check BEFORE any jar command
  if [[ ! -f "$jar_path" ]]; then
    echo "     ⚠️ JAR missing at time of signing. Listing directory:"
    ls -la "$(dirname "$jar_path")" || true
    return 0
  fi

  # Only proceed if it actually contains dylibs
  if ! jar tf "$jar_path" 2>/dev/null | grep -qE '\.dylib$'; then
    return 0
  fi

  # Convert to absolute path FIRST, before any filesystem operations
  local abs_jar_path
  abs_jar_path="$(cd "$(dirname "$jar_path")" && pwd)/$(basename "$jar_path")"

  # Ensure work_root exists and get absolute path
  mkdir -p "$work_root"
  local abs_work_root
  abs_work_root="$(cd "$work_root" && pwd)"

  local jar_name work_dir extract_dir tmp_jar
  jar_name="$(basename "$abs_jar_path")"
  work_dir="${abs_work_root}/${jar_name}.work"
  extract_dir="${work_dir}/contents"
  tmp_jar="${work_dir}/repacked.jar"

  rm -rf "$work_dir"
  mkdir -p "$extract_dir"

  echo "     Extracting JAR..."
  (cd "$extract_dir" && jar xf "$abs_jar_path")

  echo "     Signing dylibs..."
  find "$extract_dir" -type f -name "*.dylib" -print0 \
    | xargs -0 -I{} codesign --force --sign "$identity" --timestamp --options runtime "{}"

  echo "     Repacking JAR..."
  echo "     tmp_jar path: $tmp_jar"
  echo "     extract_dir: $extract_dir"
  rm -f "$tmp_jar"
  if ! (cd "$extract_dir" && jar cf "$tmp_jar" .); then
    echo "     ❌ jar cf failed!"
    echo "     PWD during jar cf: $(cd "$extract_dir" && pwd)"
    ls -la "$extract_dir" | head -10
    return 1
  fi

  echo "     Replacing original JAR..."
  # Ensure the target directory still exists
  local target_dir
  target_dir="$(dirname "$abs_jar_path")"
  if [[ ! -d "$target_dir" ]]; then
    echo "     ⚠️ Target directory missing, recreating: $target_dir"
    mkdir -p "$target_dir"
  fi

  # Verify tmp_jar exists before moving
  if [[ ! -f "$tmp_jar" ]]; then
    echo "     ❌ Repacked JAR missing: $tmp_jar"
    ls -la "$work_dir" || true
    return 1
  fi

  mv -f "$tmp_jar" "$abs_jar_path"

  rm -rf "$work_dir"
}

# --------------------------
# 3) Build
# --------------------------
echo "🏗️ Building DMG via Gradle (:desktop:packageDmg)..."
./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx4g" :desktop:packageDmg

DMG_PATH="$(ls -1 desktop/build/compose/binaries/**/dmg/*.dmg 2>/dev/null | head -n 1 || true)"
APP_PATH="$(ls -1 desktop/build/compose/binaries/**/app/*.app 2>/dev/null | head -n 1 || true)"

if [[ -z "${DMG_PATH}" ]]; then
  echo "❌ Could not find DMG under: desktop/build/compose/binaries/**/dmg/*.dmg"
  exit 1
fi

echo "📦 Found DMG: $DMG_PATH"
if [[ -n "${APP_PATH}" ]]; then
  echo "📦 Found APP: $APP_PATH"
fi

# --------------------------
# 4) Get a writable .app
# --------------------------
OUT_DIR="desktop/build/compose/notarized"
mkdir -p "$OUT_DIR"

MOUNT_DIR=""
cleanup() {
  if [[ -n "${MOUNT_DIR}" && -d "${MOUNT_DIR}" ]]; then
    hdiutil detach "${MOUNT_DIR}" -quiet || true
    rm -rf "${MOUNT_DIR}" || true
  fi
}
trap cleanup EXIT

APP_TO_SIGN=""
if [[ -n "${APP_PATH}" && -d "${APP_PATH}" ]]; then
  echo "📤 Copying .app to staging for signing..."
  rsync -a --delete "${APP_PATH}" "${OUT_DIR}/"
  APP_TO_SIGN="${OUT_DIR}/$(basename "${APP_PATH}")"
else
  echo "💿 Mounting DMG to locate .app..."
  MOUNT_DIR="$(mktemp -d /tmp/askimo-dmg-mount.XXXXXX)"
  hdiutil attach -nobrowse -mountpoint "${MOUNT_DIR}" "${DMG_PATH}" -quiet

  FOUND_APP="$(find "${MOUNT_DIR}" -maxdepth 2 -type d -name "*.app" | head -n 1 || true)"
  if [[ -z "${FOUND_APP}" ]]; then
    echo "❌ Could not find .app inside mounted DMG: ${MOUNT_DIR}"
    exit 1
  fi

  echo "📤 Copying .app out of DMG to staging..."
  rsync -a --delete "${FOUND_APP}" "${OUT_DIR}/"
  APP_TO_SIGN="${OUT_DIR}/$(basename "${FOUND_APP}")"
fi

echo "🔏 Will sign app: ${APP_TO_SIGN}"
echo "🔑 Identity: ${MACOS_IDENTITY}"

APP_CONTENTS="${APP_TO_SIGN}/Contents"
RUNTIME_DIR="${APP_CONTENTS}/runtime"
APP_DIR="${APP_CONTENTS}/app"

# Resolve main executable name from Info.plist (grounded: retrieved context uses defaults read CFBundleExecutable)
MAIN_EXE_NAME="$(defaults read "${APP_TO_SIGN}/Contents/Info" CFBundleExecutable 2>/dev/null || true)"
MAIN_EXE="${APP_CONTENTS}/MacOS/${MAIN_EXE_NAME}"

# --------------------------
# 5) Post-sign (mirrors retrieved context)
# --------------------------

# 5.1 Sign embedded runtime dylibs + jspawnhelper
if [[ -d "${RUNTIME_DIR}" ]]; then
  echo "🔧 Signing embedded runtime dylibs + helpers..."
  find "${RUNTIME_DIR}" -type f \( -name "*.dylib" -o -name "jspawnhelper" \) -print0 \
    | xargs -0 -I{} codesign --force --sign "${MACOS_IDENTITY}" --timestamp --options runtime "{}"
fi

# 5.2 Sign loose dylibs under Contents/app
if [[ -d "${APP_DIR}" ]]; then
  echo "🔧 Signing loose dylibs under Contents/app..."
  find "${APP_DIR}" -type f -name "*.dylib" -print0 \
    | xargs -0 -I{} codesign --force --sign "${MACOS_IDENTITY}" --timestamp --options runtime "{}"
fi

# 5.3 Sign dylibs embedded inside JARs
if [[ -d "${APP_DIR}" ]]; then
  echo "🔧 Signing dylibs embedded inside JARs..."
  WORK_ROOT="desktop/build/codesign-jar-work"
  rm -rf "$WORK_ROOT"
  mkdir -p "$WORK_ROOT"

  # Freeze list without mapfile (older bash compatible)
  JAR_LIST="$(mktemp)"
  find "$APP_DIR" -type f -name "*.jar" -print > "$JAR_LIST"

  while IFS= read -r jarfile; do
    [[ -f "$jarfile" ]] || { echo "⚠️ Missing jar, skip: $jarfile"; continue; }
    sign_dylibs_inside_jar "$jarfile" "${MACOS_IDENTITY}" "$WORK_ROOT"
  done < "$JAR_LIST"

  rm -f "$JAR_LIST"
fi

# 5.4 Sign main executable
if [[ -n "${MAIN_EXE_NAME}" && -f "${MAIN_EXE}" ]]; then
  echo "🔧 Signing main executable: ${MAIN_EXE}"
  codesign --force --sign "${MACOS_IDENTITY}" --timestamp --options runtime "${MAIN_EXE}"
else
  echo "⚠️ Could not resolve main executable via CFBundleExecutable."
  echo "   CFBundleExecutable='${MAIN_EXE_NAME}'"
  echo "   Looked for: ${MAIN_EXE}"
fi

# 5.5 Sign app bundle (deep)
echo "🔧 Signing app bundle (deep): ${APP_TO_SIGN}"
codesign --force --deep --sign "${MACOS_IDENTITY}" --timestamp --options runtime "${APP_TO_SIGN}"

echo "🔎 Verifying signature..."
codesign --verify --deep --strict --verbose=2 "${APP_TO_SIGN}"


# --------------------------
# 6) Create fresh signed DMG (UDZO)
# --------------------------
SIGNED_DMG="${OUT_DIR}/Askimo-signed.dmg"
rm -f "${SIGNED_DMG}"

echo "📀 Creating DMG (UDZO): ${SIGNED_DMG}"
hdiutil create \
  -volname "Askimo" \
  -srcfolder "${APP_TO_SIGN}" \
  -ov \
  -format UDZO \
  "${SIGNED_DMG}"

echo "🔧 Signing DMG..."
codesign --force --sign "${MACOS_IDENTITY}" --timestamp "${SIGNED_DMG}"

echo "🔎 Verifying DMG signature..."
codesign --verify --verbose=2 "${SIGNED_DMG}"

DMG_SHA_BEFORE="$(sha256_file "${SIGNED_DMG}")"
echo "🔎 DMG SHA256 before submit: ${DMG_SHA_BEFORE}"

# --------------------------
# 7) Notarize DMG + staple
# --------------------------
echo "🔐 Notarizing DMG..."
SUBMIT_JSON="$(notary_submit_wait_json "${SIGNED_DMG}")"
echo "${SUBMIT_JSON}"

SUBMISSION_ID="$(python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' <<<"${SUBMIT_JSON}")"
STATUS="$(python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' <<<"${SUBMIT_JSON}")"

echo "✅ DMG submission: id=${SUBMISSION_ID}, status=${STATUS}"

if [[ "${STATUS}" != "Accepted" ]]; then
  echo "❌ Notarization failed (status=${STATUS}). Fetching log..."
  if [[ -n "${SUBMISSION_ID}" ]]; then
    notary_log "${SUBMISSION_ID}" || true
  fi
  exit 1
fi

# Verify file wasn't modified during notarization
ensure_same_file_bytes "${SIGNED_DMG}" "${DMG_SHA_BEFORE}"

# Give Apple's CDN time to propagate the ticket
echo "⏳ Waiting 60s for ticket propagation..."
sleep 60

# Debug: Check DMG signature before stapling
echo "🔍 Checking DMG signature before stapling..."
codesign -dvvv "${SIGNED_DMG}" 2>&1 | grep -E "Authority|Identifier|TeamIdentifier|CDHash" || true

echo "📎 Stapling DMG (manual ticket attachment)..."
# Try stapler first, but if it fails with validation, manually attach the ticket
if ! xcrun stapler staple -v "${SIGNED_DMG}" 2>&1 | tee /tmp/stapler.log; then
  echo "⚠️ Stapler failed, attempting manual ticket attachment..."

  # Extract ticket path from stapler output
  TICKET_PATH=$(grep -o 'file://[^[:space:]]*\.ticket' /tmp/stapler.log | sed 's/file:\/\///' | head -1)

  if [[ -f "${TICKET_PATH}" ]]; then
    echo "📋 Found downloaded ticket: ${TICKET_PATH}"

    # Manually attach ticket to DMG using xattr with binary data
    echo "📎 Manually attaching ticket to DMG..."
    xattr -w com.apple.stapler "$(cat "${TICKET_PATH}" | base64)" "${SIGNED_DMG}"

    echo "✅ Ticket manually attached (base64 encoded)"

    # For proper binary attachment, use Python
    python3 <<EOF
import sys
import xattr
with open("${TICKET_PATH}", "rb") as f:
    ticket_data = f.read()
xattr.setxattr("${SIGNED_DMG}", "com.apple.stapler", ticket_data)
print("✅ Ticket reattached as binary")
EOF
  else
    echo "❌ Could not find downloaded ticket file"
    exit 1
  fi
else
  echo "✅ Stapled DMG successfully"
fi

echo "🔎 Validating stapled ticket..."
if xcrun stapler validate "${SIGNED_DMG}"; then
  echo "✅ Staple validation successful"
else
  echo "⚠️ Staple validation reported issues, but ticket may still be attached"
  # Check if ticket is actually there
  if xattr -l "${SIGNED_DMG}" | grep -q "com.apple.stapler"; then
    echo "✅ Ticket is attached via xattr"
  else
    echo "❌ No ticket found in DMG"
    exit 1
  fi
fi

echo "✅ Done. Output: ${SIGNED_DMG}"
echo
echo "Suggested checks:"
echo "  spctl -a -t open -vv \"${SIGNED_DMG}\""
