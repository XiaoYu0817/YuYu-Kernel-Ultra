#!/usr/bin/env bash
set -euo pipefail

# Pinned to a known-good release on purpose: looking up the latest release
# through the (unauthenticated) GitHub API gets rate limited on shared CI
# runners, which silently produced a non-executable file and broke the build.
SCCACHE_VERSION="${SCCACHE_VERSION:-v0.19.0}"

# $1=variant
# $2=install_dir
# $3=exe
install_from_gh() {
  local url="https://github.com/mozilla/sccache/releases/download/${SCCACHE_VERSION}/sccache-${SCCACHE_VERSION}-$1.tar.gz"
  local dest="$2/$3"
  curl -fsSL "$url" | tar xz -O --wildcards "*/$3" > "$dest"
  chmod +x "$dest"
  if ! "$dest" --version > /dev/null 2>&1; then
    rm -f "$dest"
    echo "failed to install sccache (${SCCACHE_VERSION} / $1)" >&2
    return 1
  fi
}

if [ "$RUNNER_OS" = "macOS" ]; then
  brew install sccache
elif [ "$RUNNER_OS" = "Linux" ]; then
  install_from_gh x86_64-unknown-linux-musl /usr/local/bin sccache
elif [ "$RUNNER_OS" = "Windows" ]; then
  install_from_gh x86_64-pc-windows-msvc "$USERPROFILE/.cargo/bin" sccache.exe
fi
