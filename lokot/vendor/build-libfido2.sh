#!/bin/sh
# Builds the vendored libfido2 for a Linux target and stages it where Gradle looks for it.
#
# For this machine:
#
#   sudo apt install build-essential cmake pkg-config libcbor-dev libssl-dev zlib1g-dev libudev-dev
#   ./gradlew build          # fails at the link, but fetches the Kotlin/Native toolchain
#   ./vendor/build-libfido2.sh
#   ./gradlew build
#
# For a Raspberry Pi, from an x86_64 Linux machine — the Kotlin/Native compiler runs only on Linux
# x86_64, Windows x86_64 and macOS, so an aarch64 binary is always cross-built:
#
#   # once, from the Pi, which needs the same -dev packages installed:
#   ssh pi 'tar -cf - -C / usr/include/cbor.h usr/include/cbor usr/include/openssl \
#       usr/include/aarch64-linux-gnu/openssl usr/include/zlib.h usr/include/zconf.h \
#       usr/include/libudev.h usr/lib/aarch64-linux-gnu/pkgconfig/lib{cbor,crypto,ssl,udev}.pc \
#       usr/lib/aarch64-linux-gnu/pkgconfig/{openssl,zlib}.pc \
#       usr/lib/aarch64-linux-gnu/lib{cbor,crypto,udev,z}.so*' \
#     | tar -xf - -C vendor/downloads/sysroot-aarch64
#
#   # some of those .so symlinks are absolute (libz.so -> /lib/...) and dangle outside the copy:
#   find vendor/downloads/sysroot-aarch64 -type l -lname '/*' \
#       -exec sh -c 'ln -sfn "$(basename "$(readlink "$1")")" "$1"' _ {} \;
#
#   ./gradlew build -Plokot.arch=aarch64
#   LOKOT_ARCH=aarch64 ./vendor/build-libfido2.sh
#   ./gradlew build -Plokot.arch=aarch64
#
# Run it once per target. The x86_64 result is committed, so a clone on that architecture already
# has it.
#
# Why lokot builds its own at all: hmac-secret-mc, the CTAP 2.2 extension that lets enrolment
# return the derived bytes so the user touches the key once instead of twice, arrived in libfido2
# 1.17.0. Ubuntu 24.04 packages 1.14.0, Debian 12 1.12.0. Windows already links lokot's own build;
# this is the same source, patched the same way.
#
# Why it uses Kotlin/Native's toolchain rather than the system compiler: K/N links through its own
# glibc 2.19 sysroot. A libfido2 built against a modern glibc pulls in the C23 aliases that
# _GNU_SOURCE turns on from 2.38 — __isoc23_sscanf, __isoc23_strtoull — and those do not exist in
# that sysroot, so the link fails on symbols the host libc actually has. Compiling against the same
# sysroot K/N links against avoids the mismatch, and makes the result run on any glibc >= 2.19.
#
# Static, so the binary carries libfido2 itself whatever the target packages. libcbor, libcrypto,
# libudev and libz stay dynamic: stable system libraries, and pinning them would mean vendoring
# OpenSSL too.
set -eu

version=1.17.0
here=$(cd "$(dirname "$0")" && pwd)
source_dir="$here/downloads/libfido2-$version"
machine=${LOKOT_ARCH:-$(uname -m)}
build_dir="$source_dir/build-konan-$machine"
target="$here/libfido2/linux-$machine"

[ -d "$source_dir" ] || { echo "$source_dir is missing: unpack the libfido2 $version release there" >&2; exit 1; }

# The patch travels applied, in the source, so a build on any host produces the same library.
grep -q '"android-key"' "$source_dir/src/cbor.c" ||
    { echo "$source_dir is not patched: apply patches/0001-accept-android-key-attestation.patch" >&2; exit 1; }

konan="${KONAN_DATA_DIR:-$HOME/.konan}/dependencies"
llvm=$(ls -d "$konan"/llvm-*-linux-essentials-* 2>/dev/null | head -1)
gcc_toolchain=$(ls -d "$konan"/"$machine"-unknown-linux-gnu-gcc-*-glibc-* 2>/dev/null | head -1)
[ -n "$llvm" ] && [ -n "$gcc_toolchain" ] ||
    { echo "no Kotlin/Native $machine toolchain under $konan - run ./gradlew build once to fetch it" >&2; exit 1; }
sysroot="$gcc_toolchain/$machine-unknown-linux-gnu/sysroot"

# Where the third-party headers and shared libraries come from: this machine for a native build,
# the copy taken off the target for a cross one. Searched with -idirafter, after the K/N sysroot,
# so only what that lacks is picked up — libc stays the sysroot's, which is the whole point.
if [ "$machine" = "$(uname -m)" ]; then
    extras=""
    cross=""
    # No -L for this machine's libraries: it would put the host's libc ahead of the sysroot's, and
    # cmake's compiler probe would then fail on symbols glibc has since dropped. Nothing needs it,
    # since only the static library is built here.
    extras_lib=""
else
    extras="$here/downloads/sysroot-$machine"
    [ -d "$extras/usr/include" ] ||
        { echo "$extras is missing - copy the target's headers and libraries there, see the top of this file" >&2; exit 1; }
    cross="-DCMAKE_SYSTEM_NAME=Linux -DCMAKE_SYSTEM_PROCESSOR=$machine"
    # Safe here, unlike the native case: this holds only the target's cbor/crypto/udev/z, no libc.
    extras_lib="-L$extras/usr/lib/$machine-linux-gnu"
    # Otherwise pkg-config answers with this machine's libraries.
    PKG_CONFIG_LIBDIR="$extras/usr/lib/$machine-linux-gnu/pkgconfig"
    PKG_CONFIG_SYSROOT_DIR="$extras"
    export PKG_CONFIG_LIBDIR PKG_CONFIG_SYSROOT_DIR
fi

# Only the compiler is taken from Kotlin/Native's LLVM; ar and ranlib stay the host's, since the
# bundle ships neither an llvm-ranlib nor anything else needed to write an archive index.
# shellcheck disable=SC2086
PATH="$llvm/bin:$PATH" cmake -S "$source_dir" -B "$build_dir" $cross \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_STATIC_LIBS=ON \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_MANPAGES=OFF \
    -DBUILD_TOOLS=OFF \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_C_COMPILER="$llvm/bin/clang" \
    -DCMAKE_C_FLAGS="--target=$machine-unknown-linux-gnu --sysroot=$sysroot --gcc-toolchain=$gcc_toolchain -idirafter $extras/usr/include -idirafter $extras/usr/include/$machine-linux-gnu" \
    -DCMAKE_EXE_LINKER_FLAGS="--sysroot=$sysroot --gcc-toolchain=$gcc_toolchain -fuse-ld=lld $extras_lib"
PATH="$llvm/bin:$PATH" cmake --build "$build_dir" -j"$(nproc)" --target fido2

# The whole point of the sysroot detour, so fail here rather than at the Kotlin link.
if nm -u "$build_dir/src/libfido2.a" | grep -q isoc23; then
    echo "libfido2.a still references __isoc23_* - the target's libc headers won over the sysroot's" >&2
    exit 1
fi

mkdir -p "$target"
cp "$build_dir/src/libfido2.a" "$target/libfido2.a"
echo "staged $target/libfido2.a"
