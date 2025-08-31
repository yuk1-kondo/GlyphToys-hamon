# Changelog

## 2025-08-31

### Fixed
- RippleWaveToy not rendering on device
  - Corrected SDK package imports to `com.nothing.ketchum.*`
  - Added try/catch for `GlyphException` on `setMatrixFrame`
  - Converted frame buffer format from ARGB to 0..2040 brightness array based on SDK behavior (driver logs showed 0..2040 values)
  - Increased base brightness and contrast for clearer visibility on matrix
  - Added registration fallback across `Glyph.DEVICE_*` codes and disabled glyph matrix timeout when possible
  - Cleaned `AndroidManifest.xml` (removed deprecated `package` attribute)
  - Split flavors: `device` (full SDK) and `emulator` (stub), removed main duplicate

### Added
- Logging for service connection, registration result, long-press events, and setFrame fallback

### Build
- `app/build.gradle`: ensure `libs/*.aar` is on the compile classpath; min/target SDK unchanged


