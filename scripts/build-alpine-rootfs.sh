#!/usr/bin/env bash
# full APK 用のカスタム Alpine rootfs を生成する。成果物は意図的に src/full のみへ置く。
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ALPINE_VERSION="${ALPINE_VERSION:-3.21.0}"
ALPINE_BRANCH="${ALPINE_BRANCH:-v3.21}"
TARGET_ARCH="aarch64"
HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
  x86_64|amd64) APK_ARCH=x86_64 ;;
  aarch64|arm64) APK_ARCH=aarch64 ;;
  *) echo "unsupported host architecture: $HOST_ARCH" >&2; exit 1 ;;
esac

WORK="$PROJECT_ROOT/build/alpine-rootfs"
APK_DIR="$WORK/apk-tools"
KEYS_DIR="$WORK/keys"
ROOTFS="$WORK/$TARGET_ARCH/rootfs"
OUTPUT="$PROJECT_ROOT/app/src/full/assets/alpine-minirootfs-aarch64.tgz"
mkdir -p "$APK_DIR" "$KEYS_DIR" "$ROOTFS" "$(dirname "$OUTPUT")"

CURL=(curl -fL --retry 5 --retry-delay 3 --retry-connrefused --connect-timeout 20)
REPO="https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/main/$APK_ARCH"
APK_STATIC="$APK_DIR/sbin/apk.static"
if [[ ! -x "$APK_STATIC" ]]; then
  pkg="$("${CURL[@]}" -s "$REPO/" | grep -oE 'apk-tools-static-[0-9][^"<]*\.apk' | sort -V | tail -n1)"
  test -n "$pkg"
  "${CURL[@]}" "$REPO/$pkg" -o "$APK_DIR/apk.apk"
  tar -xzf "$APK_DIR/apk.apk" -C "$APK_DIR"
fi
if [[ -z "$(find "$KEYS_DIR" -type f -name '*.rsa.pub' -print -quit)" ]]; then
  pkg="$("${CURL[@]}" -s "$REPO/" | grep -oE 'alpine-keys-[0-9][^"<]*\.apk' | sort -V | tail -n1)"
  test -n "$pkg"
  tmp="$(mktemp -d)"
  "${CURL[@]}" "$REPO/$pkg" -o "$tmp/keys.apk"
  tar -xzf "$tmp/keys.apk" -C "$tmp"
  find "$tmp" -type f -name '*.rsa.pub' -exec cp {} "$KEYS_DIR/" \;
fi

rm -rf "$ROOTFS"
mkdir -p "$ROOTFS"
mapfile -t PACKAGES < <(grep -vE '^\s*(#|$)' "$PROJECT_ROOT/scripts/alpine-packages.txt")
RUN=()
if [[ "$(id -u)" -ne 0 ]] && command -v fakeroot >/dev/null; then RUN=(fakeroot --); fi
"${RUN[@]}" "$APK_STATIC" --root "$ROOTFS" --keys-dir "$KEYS_DIR" --arch "$TARGET_ARCH" \
  --repository "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/main" \
  --repository "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/community" \
  --no-cache --initdb --no-scripts add "${PACKAGES[@]}"

mkdir -p "$ROOTFS/etc/apk" "$ROOTFS/etc/profile.d" "$ROOTFS/root"
printf '%s\n' \
  "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/main" \
  "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_BRANCH/community" > "$ROOTFS/etc/apk/repositories"
printf '%s\n' 'export TERM="${TERM:-xterm-256color}"' 'export LANG="${LANG:-C.UTF-8}"' \
  'export LC_ALL="${LC_ALL:-C.UTF-8}"' 'export EDITOR="${EDITOR:-vi}"' > "$ROOTFS/etc/profile.d/z2term.sh"
printf '%s\n' '[ -f /etc/profile ] && . /etc/profile' "PROMPT='%F{green}%n@z2term%f:%F{blue}%~%f%# '" > "$ROOTFS/root/.zshrc"
sed -i -E 's|^(root:[^:]*:0:0:[^:]*:[^:]*:)[^:]*$|\1/bin/zsh|' "$ROOTFS/etc/passwd"
find "$ROOTFS" -type f -exec chmod u+r {} +
find "$ROOTFS" -type d -exec chmod u+rx {} +

tar --owner=0 --group=0 --numeric-owner --format=ustar -C "$ROOTFS" -cf - . | gzip -9 > "$OUTPUT"
test -s "$OUTPUT"
echo "created: $OUTPUT ($(du -h "$OUTPUT" | cut -f1))"
