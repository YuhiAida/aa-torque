**Current status:** The `build_deploy` job on `master` fails in `packageRelease` with:

```
com.android.ide.common.signing.KeytoolException: Failed to read key androiddebugkey from store "/home/runner/debug.keystore": null
```

This breaks the entire release pipeline. No APK is produced, no release is created, and the `build_deploy` job (which `needs: lint`) ends with conclusion `failure`. The `lint` job (ktlintCheck + lintRelease) still passes.

**Evidence:** Two consecutive master builds (the merged PR #19 and PR #20 runs) fail with this exact exception. PR #20 ("declare the keystore type as JKS") changed the failure from
`"Tag number over 30 is not supported"` to `"...read key androiddebugkey ...: null"`,
so the underlying keystore problem is NOT resolved.

**Root cause (as far as readable evidence allows):** the `DEBUG_KEYSTORE` GitHub secret, when decoded to `$HOME/debug.keystore` and read by the GitHub runner's JDK 19, does not yield a key under alias `androiddebugkey`. This can be a store-type mismatch (secret is PKCS12 but AGP is told JKS, or the reverse), a different key alias, or a corrupted/truncated secret. The `Restore keystore` step already logs the file size and first four bytes (magic bytes: JKS = `fe ed fe ed`, PKCS12 = `30`), so a wrong type now fails immediately instead of two minutes later.

**Reproduction:** push to `master` (or merge any PR that triggers `build_deploy`). The `test`/build portion ends in `failure`.

**Proposed fix (inspect the secret before changing CI):**
1. Regenerate a keystore compatible with the CI config: JKS, alias `androiddebugkey`, store password `android`, key password `android`; OR
2. Repair/regenerate the `DEBUG_KEYSTORE` secret and verify the magic bytes in the `Restore keystore` log.

**Impact:** no release APKs are published; users cannot receive updates. The `lint` pipeline is unaffected.
