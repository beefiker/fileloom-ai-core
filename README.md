# fileloom-ai-core

Pure Kotlin Illustrator `.ai` parsing and SVG rendering core for Fileloom.

## Validation

```bash
./gradlew test -Pversion=0.1.0
```

## Maven Central Bundle

Create the signed upload ZIP with a non-interactive GPG passphrase:

```bash
SIGNING_GNUPG_PASSPHRASE='your-passphrase' ./gradlew publishToMavenCentralBundle -Pversion=0.1.0
```

The task writes:

```text
build/maven-central-bundle/fileloom-ai-core-0.1.0-maven-central-bundle.zip
```

Use `-Psigning.gnupg.keyName=<KEY_ID>` if the default GPG secret key is not the
publishing key. The ZIP still needs to be uploaded manually at Maven Central.
