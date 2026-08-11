# Protocol Builder

Reads a GE `Protocols.xlsm` workbook with Apache POI and prints a compact summary of detected protocol worksheets. Recognized fields populate the typed model; notes/comments and unknown worksheet fields are retained in `Protocol.notes` and `Protocol.advanced`.

## Run

Requires Java 8+. Always use the Gradle **wrapper** (`gradlew`), not a system-installed `gradle` binary — the wrapper pins the exact Gradle version (8.x) this build is written for, and a distro-packaged `gradle` is often years out of date and will fail with confusing errors on this `build.gradle`.

macOS/Linux:

```bash
./gradlew test
./gradlew run --args="/path/to/Protocols.xlsm"
```

Windows:

```powershell
gradlew.bat test
gradlew.bat run --args="C:\path\to\Protocols.xlsm"
```

With `Protocols.xlsm` in this directory, `./gradlew run` (or `gradlew.bat run`) is sufficient. Parse/validation failures are printed as `ERROR:` messages and return exit code 2.

### Troubleshooting: `gradlew` fails with a certificate/PKIX error

The wrapper downloads its pinned Gradle distribution over HTTPS on first run. If that fails with `unable to find valid certification path to requested target`, it's a JVM trust-store problem, not a bad download URL. On Ubuntu (including EC2 images), this is usually the `ca-certificates-java` package's `cacerts` file being out of sync with the OS-level CA bundle that tools like `curl` already trust. Fix it with:

```bash
sudo dpkg-reconfigure ca-certificates-java
```

then re-run `./gradlew`.

## VS Code

Install **Extension Pack for Java** and **Gradle for Java**, open this `protocol_builder` folder, then run the Gradle `application > run` task. To pass a workbook path, use the integrated terminal command above.
