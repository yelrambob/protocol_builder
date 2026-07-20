# Protocol Builder

Reads a GE `Protocols.xlsm` workbook with Apache POI and prints a compact summary of detected protocol worksheets. Recognized fields populate the typed model; notes/comments and unknown worksheet fields are retained in `Protocol.notes` and `Protocol.advanced`.

## Run

Requires Java 8+ and Gradle 8.x (or the included wrapper once generated).

```powershell
gradle test
gradle run --args="C:\path\to\Protocols.xlsm"
```

With `Protocols.xlsm` in this directory, `gradle run` is sufficient. Parse/validation failures are printed as `ERROR:` messages and return exit code 2.

## VS Code

Install **Extension Pack for Java** and **Gradle for Java**, open this `protocol_builder` folder, then run the Gradle `application > run` task. To pass a workbook path, use the integrated terminal command above.
