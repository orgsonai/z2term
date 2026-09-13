#!/usr/bin/env bash
# Build the pinned public-tunnel connector for Android; no Linux distro is needed at runtime.
set -euo pipefail
QR_PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
QR_VERSION=2026.9.1
QR_SHA256=e75d9a314ae07ae42564c0d3115b9e33d8a7329d99771951ab7b67144461bd84
QR_CACHE="${Z2_TUNNEL_BUILD_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/z2term/tunnel/${QR_VERSION}}"
QR_OUTPUT="${QR_PROJECT}/app/src/main/jniLibs/arm64-v8a/libz2tunnel.so"
QR_NOTICES="${QR_PROJECT}/app/src/main/assets/licenses/QR-Tunnel.txt"
QR_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$QR_SDK" && -f "${QR_PROJECT}/local.properties" ]]; then
    QR_SDK="$(sed -n 's/^sdk.dir=//p' "${QR_PROJECT}/local.properties" | head -n1 | tr -d '\r')"
fi
QR_NDK="${ANDROID_NDK_HOME:-}"
if [[ -z "$QR_NDK" ]]; then
    QR_NDK="$(find "${QR_SDK}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
fi
case "$(uname -s)" in
    Linux) QR_HOST=linux-x86_64 ;;
    Darwin) QR_HOST=darwin-x86_64 ;;
    *) echo 'Unsupported build host' >&2; exit 1 ;;
esac
QR_CC="${QR_NDK}/toolchains/llvm/prebuilt/${QR_HOST}/bin/aarch64-linux-android29-clang"
[[ -x "$QR_CC" ]] || { echo 'Android NDK required: set ANDROID_NDK_HOME' >&2; exit 1; }
command -v go >/dev/null || { echo 'Go 1.26 or newer is required to build QR sharing' >&2; exit 1; }
mkdir -p "$QR_CACHE" "$(dirname "$QR_OUTPUT")" "$(dirname "$QR_NOTICES")"
if [[ ! -f "${QR_CACHE}/source.tar.gz" ]]; then
    curl --fail --location --retry 2 "https://codeload.github.com/cloudflare/cloudflared/tar.gz/refs/tags/${QR_VERSION}" -o "${QR_CACHE}/source.tar.gz.tmp"
    mv "${QR_CACHE}/source.tar.gz.tmp" "${QR_CACHE}/source.tar.gz"
fi
printf '%s  %s\n' "$QR_SHA256" "${QR_CACHE}/source.tar.gz" | sha256sum --check --status
tar -xzf "${QR_CACHE}/source.tar.gz" -C "$QR_CACHE"
cd "${QR_CACHE}/cloudflared-${QR_VERSION}"
QR_WORKERS=$(( $(getconf _NPROCESSORS_ONLN) / 2 ))
[[ "$QR_WORKERS" -gt 0 ]] || QR_WORKERS=1
export GOOS=android GOARCH=arm64 CGO_ENABLED=1 CC="$QR_CC" GOMAXPROCS="$QR_WORKERS"
go build -p "$QR_WORKERS" -mod=readonly -trimpath -buildmode=pie \
    -ldflags "-s -w -X main.Version=${QR_VERSION} -X main.BuildTime=2026-09-14" \
    -o "${QR_OUTPUT}.tmp" ./cmd/cloudflared
go list -p "$QR_WORKERS" -mod=readonly -deps -json ./cmd/cloudflared > "${QR_CACHE}/packages.json"
python3 "${QR_PROJECT}/scripts/qr-tunnel-notices.py" "${QR_CACHE}/packages.json" "$QR_NOTICES"
chmod 0755 "${QR_OUTPUT}.tmp"
mv "${QR_OUTPUT}.tmp" "$QR_OUTPUT"
printf 'Built Android QR tunnel %s\n' "$QR_VERSION"
