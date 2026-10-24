# Release signing

Releases are signed with **one permanent keystore**, kept in GitHub repository secrets. Android only lets an
APK update an installed app if both were signed with the same key, so a key that changes (or a throwaway key
made on every build) forces shops to uninstall and lose their data. There are no default passwords: a release
without these secrets stops with a clear error.

## One-time setup

```bash
keytool -genkeypair -v -keystore minimart-release.jks -alias minimart \
  -keyalg RSA -keysize 4096 -validity 10000
# it asks for a store password and a key password: use long random ones

base64 -w0 minimart-release.jks | gh secret set SIGNING_KEYSTORE_BASE64
gh secret set SIGNING_STORE_PASSWORD      # the store password
gh secret set SIGNING_KEY_ALIAS           # minimart
gh secret set SIGNING_KEY_PASSWORD        # the key password
```

**Back up `minimart-release.jks` and both passwords somewhere safe and private (a password manager, an
offline copy).** If they are lost, no update can ever be signed for the installed app. Never commit them
(`*.jks` is git-ignored).

## Secrets the release workflow reads

| Secret | What it is |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the `.jks` file, base64 encoded |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias inside the keystore |
| `SIGNING_KEY_PASSWORD` | key password |

## Building a signed release on your own computer

Put the keystore at `app/minimart-release.jks` (or set `SIGNING_KEYSTORE_PATH`), export the three
`SIGNING_*` password/alias variables, then run `gradle assembleRelease`.
