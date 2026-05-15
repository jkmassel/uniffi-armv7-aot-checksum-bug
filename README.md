# uniffi-armv7-aot-checksum-bug

Minimal reproducer for a UniFFI Kotlin-bindings crash that fires on
Android `armeabi-v7a` devices when the app's bytecode has been
AOT-compiled. The same APK either crashes or passes depending on a
single ADB command:

```sh
adb shell cmd package compile -m speed  -f com.example.uniffireproaot   # crashes
adb shell cmd package compile -m verify -f com.example.uniffireproaot   # passes
```

The crash is the familiar UniFFI init-time exception:

```
FATAL EXCEPTION: main
java.lang.ExceptionInInitializerError
  at uniffi.uniffi_repro.Uniffi_reproKt.uniffiEnsureInitialized(uniffi_repro.kt:1174)
Caused by: java.lang.RuntimeException: UniFFI API checksum mismatch: try cleaning and rebuilding your project
  at uniffi.uniffi_repro.Uniffi_reproKt.uniffiCheckApiChecksums(uniffi_repro.kt:1127)
  at uniffi.uniffi_repro.IntegrityCheckingUniffiLib.<clinit>(uniffi_repro.kt:793)
```

## Why this is a UniFFI problem

The root cause is in Android's ART AOT compiler on `armeabi-v7a` — it
skips a `jshort` reinterpretation step that JVMS requires and that the
interpreter and JIT both perform correctly. UniFFI didn't introduce that
bug, but its checksum bindings happen to be one of the few mainstream
Kotlin/Android code shapes that exercises it.

The crash is hitting roughly **117 users** in production across
WordPress and Jetpack for Android (Sentry-tracked), spanning Samsung A02
/ A13, TECNO, Infinix, Nokia, Realme C11, ZTE, itel, LG, and a long tail
of budget-Android OEMs on Android 8.1 through 16. All affected events
ship the `armeabi-v7a` split APK. Original investigation thread:
[Automattic/wordpress-rs#1339](https://github.com/Automattic/wordpress-rs/issues/1339).

A patch to ART won't reach the already-affected devices for years, so
the practical fix has to come from UniFFI's codegen.

## Mechanism

UniFFI's checksum function is a Rust `extern "C" fn() -> u16 { 0xD2FF }`.
UniFFI declares the matching Kotlin native method as
`external fun foo(): Short` (JNI descriptor `()S`) and binds it via JNA's
direct-mapping `Native.register(...)`.

At the JNI boundary, ART has to convert the 16-bit native return value
into a Java `short` and put it on the operand stack as a sign-extended
`int` (per JVMS §2.11.1 / §2.11.8). Java's `short` is signed, so the
unsigned `0xD2FF` from the native side becomes the signed `int -11521`
on the operand stack, which is what the bindings' `sipush -11521`
compares against.

- **Interpreter and JIT do this conversion correctly** — checksum
  compares match, app runs fine.
- **`armeabi-v7a` AOT skips the conversion entirely.** The bindings see
  the raw native value `54015` instead of `-11521`, the comparison fails,
  and `uniffiCheckApiChecksums` throws.

The AOT path on `arm64-v8a` does *not* exhibit this bug; only
`armeabi-v7a`. That's why every production Sentry event for this crash is on a device
with `armeabi-v7a` as its primary ABI.

## Reproduction

### Requirements

- JDK 17, Android SDK platform 34 + `build-tools` 34+ (AGP 8.5.2, Kotlin
  2.2.21).
- A **32-bit-capable physical Android device** — e.g. Pixel 6/6a, any
  Samsung Galaxy S through S23, or any of the actually-affected budget
  devices (A02, A13, J4+, C11, etc.). Firebase Test Lab and AWS Device
  Farm both list suitable phones for remote runs.
- **No emulator on Apple Silicon** — Google's `arm64-v8a` emulator
  images all ship with `abilist32=` empty, and the emulator launcher
  refuses to start an `armeabi-v7a` AVD on an `aarch64` host. Use a
  physical device.

Developed and verified on a Samsung Galaxy S9 (`SM-G960W`, Android 10,
API 29).

### Build and install

The repo commits both `libuniffi_repro.so` and the generated Kotlin
bindings, so you don't need a Rust+NDK toolchain to reproduce:

```sh
cd android
./gradlew :app:assembleDebug

adb shell getprop ro.product.cpu.abilist32   # must contain "armeabi-v7a"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run

A freshly-installed app starts in `verify` mode (no AOT). The check
passes:

```sh
adb logcat -c
adb shell am start -n com.example.uniffireproaot/com.example.repro.MainActivity
adb logcat -d -s AotRepro AndroidRuntime
# Expected:
#   uniffiEnsureInitialized OK
```

Force the app into AOT mode and re-run:

```sh
adb shell cmd package compile -m speed -f com.example.uniffireproaot
adb shell am force-stop com.example.uniffireproaot
adb logcat -c
adb shell am start -n com.example.uniffireproaot/com.example.repro.MainActivity
adb logcat -d -s AotRepro AndroidRuntime
# Expected:
#   Caused by: java.lang.RuntimeException: UniFFI API checksum mismatch
```

Flip back and forth with:

```sh
adb shell cmd package compile -m verify -f com.example.uniffireproaot   # passes
adb shell cmd package compile -m speed  -f com.example.uniffireproaot   # crashes
adb shell am force-stop       com.example.uniffireproaot                # so next launch reloads
```

### Rebuilding from source

If you want to regenerate the native lib and bindings against a different
`uniffi-rs` commit:

```sh
cd rust
rustup target add armv7-linux-androideabi
cargo install cargo-ndk
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/28.1.13356709"
cargo ndk -t armeabi-v7a build --release

cargo build --release   # also build host .so for bindgen library-mode
cargo run --bin uniffi-bindgen --release -- generate \
    --library target/release/libuniffi_repro.dylib \
    --language kotlin \
    --out-dir generated

cp target/armv7-linux-androideabi/release/libuniffi_repro.so \
   ../android/app/src/main/jniLibs/armeabi-v7a/
cp generated/uniffi/uniffi_repro/uniffi_repro.kt \
   ../android/app/src/main/java/uniffi/uniffi_repro/

cd ../android && ./gradlew :app:assembleDebug
```

The committed binaries were built against the `uniffi-rs` commit pinned
in `rust/Cargo.toml` (currently `main`).

## What the demo activity does

[`MainActivity.kt`](android/app/src/main/java/com/example/repro/MainActivity.kt):

1. Calls `uniffi.uniffi_repro.uniffiEnsureInitialized()` — triggers
   `<clinit>` on `IntegrityCheckingUniffiLib` and runs the full checksum
   check. In AOT mode this throws.
2. If init succeeded, it reflects into one of the checksum native methods
   (`uniffi_uniffi_repro_checksum_func_double`, picked because its
   checksum has the high bit set) and prints both the sign-extended
   `Int` and the unsigned low-16 `Int`. In `verify` mode they match (and
   the checksum check passed). In AOT mode the activity crashes first.

The crash fires on the first checksum value with the high bit set
(`>= 0x8000`). Empirically about half of UniFFI-generated checksums land
in that range, so any reasonably sized bindings surface contains several.

## Candidate fixes in UniFFI

1. **Generate `()I` returning `Int` instead of `()S` returning `Short`.**
   JNI returns a 32-bit `int` directly with no conversion involved, so
   the buggy AOT path has nothing to skip. Bindings emit the
   unsigned-style int constant (`54015`) instead of the signed-short
   form (`sipush -11521`). Requires changes to `uniffi-bindgen` (Kotlin
   generator) and to the proc-macro that emits the native scaffolding
   function signatures.

2. **Mask before compare in Kotlin.** Keep the `()S` signature; change
   the generated comparison from `if (lib.foo() != 54015.toShort())` to
   `if ((lib.foo().toInt() and 0xFFFF) != 54015)`. Forces an int
   comparison that doesn't depend on the JNI conversion step. Smallest
   delta — purely a `uniffi-bindgen-kotlin` change.

Option 1 is the cleaner fix; option 2 ships faster.

## Layout

```
rust/                                  # Minimal UniFFI Rust crate
├── Cargo.toml                         # depends on uniffi-rs main
├── .cargo/config.toml                 # Android NDK linkers
└── src/
    ├── lib.rs                         # 17 #[uniffi::export] functions
    └── bin/uniffi-bindgen.rs          # bindgen CLI entry point

android/                               # Android Gradle project
├── settings.gradle.kts
├── build.gradle.kts
├── app/
│   ├── build.gradle.kts               # armeabi-v7a-only, JNA 5.18.1
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/repro/MainActivity.kt
│       ├── java/uniffi/uniffi_repro/uniffi_repro.kt   # generated
│       └── jniLibs/armeabi-v7a/libuniffi_repro.so     # built
└── gradlew + gradle/wrapper
```
