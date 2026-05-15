// Minimal UniFFI Rust crate. Each #[uniffi::export]'d function gets a 16-bit
// checksum baked into both the .so and the generated Kotlin bindings.
// The bug triggers on the first checksum whose value has the high bit set
// (>= 0x8000), so we expose enough functions that statistically several of
// them land in that range.

uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn ping_a() -> String { "a".into() }

#[uniffi::export]
pub fn ping_b() -> String { "b".into() }

#[uniffi::export]
pub fn ping_c() -> String { "c".into() }

#[uniffi::export]
pub fn ping_d() -> String { "d".into() }

#[uniffi::export]
pub fn ping_e() -> String { "e".into() }

#[uniffi::export]
pub fn add(a: u32, b: u32) -> u32 { a + b }

#[uniffi::export]
pub fn sub(a: u32, b: u32) -> u32 { a - b }

#[uniffi::export]
pub fn mul(a: u32, b: u32) -> u32 { a * b }

#[uniffi::export]
pub fn div(a: u32, b: u32) -> u32 { a / b }

#[uniffi::export]
pub fn join(s: String, t: String) -> String { format!("{s}{t}") }

#[uniffi::export]
pub fn upper(s: String) -> String { s.to_uppercase() }

#[uniffi::export]
pub fn lower(s: String) -> String { s.to_lowercase() }

#[uniffi::export]
pub fn double(x: u32) -> u32 { x * 2 }

#[uniffi::export]
pub fn triple(x: u32) -> u32 { x * 3 }

#[uniffi::export]
pub fn negate(x: i32) -> i32 { -x }

#[uniffi::export]
pub fn is_even(x: u32) -> bool { x % 2 == 0 }

// Demonstrates the silent-value-corruption case: a user-facing function
// returning `u16` with the high bit set. Should return 0xD2FF = 54015
// (or as Kotlin Short, -11521). On armeabi-v7a + AOT, the buggy ART JNI
// bridge will hand the Kotlin code a different value.
#[uniffi::export]
pub fn get_short_high_bit() -> u16 { 0xD2FF }

#[uniffi::export]
pub fn get_signed_short_high_bit() -> i16 { -11521 }

#[uniffi::export]
pub fn get_byte_high_bit() -> u8 { 0xAB }   // 171 unsigned, -85 if interpreted as signed

#[uniffi::export]
pub fn get_signed_byte_high_bit() -> i8 { -85 }  // 0xAB as bit pattern

#[uniffi::export]
pub fn get_bool_true() -> bool { true }

#[uniffi::export]
pub fn get_bool_false() -> bool { false }

// Stress LLVM's sign-extension across the full i16 / i8 range:
// values that have to take different codegen paths (movw, mvn, movw+sxth, etc.).
#[uniffi::export]
pub fn get_i16_neg_min() -> i16 { -32768 }    // 0x8000 — most negative
#[uniffi::export]
pub fn get_i16_neg_one() -> i16 { -1 }        // 0xFFFF — all bits set
#[uniffi::export]
pub fn get_i16_neg_127() -> i16 { -127 }      // 0xFF81 — small-magnitude negative
#[uniffi::export]
pub fn get_i16_neg_257() -> i16 { -257 }      // 0xFEFF — bigger negative
#[uniffi::export]
pub fn get_i16_pos_max() -> i16 { 32767 }     // 0x7FFF — most positive, no high bit
#[uniffi::export]
pub fn get_i16_zero() -> i16 { 0 }

#[uniffi::export]
pub fn get_i8_neg_min() -> i8 { -128 }        // 0x80 — most negative
#[uniffi::export]
pub fn get_i8_neg_one() -> i8 { -1 }          // 0xFF — all bits set
#[uniffi::export]
pub fn get_i8_neg_42() -> i8 { -42 }          // 0xD6 — high bit set, mid-range
#[uniffi::export]
pub fn get_i8_pos_max() -> i8 { 127 }         // 0x7F — most positive
#[uniffi::export]
pub fn get_i8_zero() -> i8 { 0 }
