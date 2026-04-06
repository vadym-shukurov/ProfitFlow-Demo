## Maintainer agent history

- 2026-04-06: Initialized agent charter and repo-native memory.
- 2026-04-06: Improved local testability on newer JDKs (e.g., 25):
  - Forced Mockito to use subclass mock maker via `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`.
  - Added Surefire/Failsafe `argLine` flags for dynamic agent loading (harmless in CI on Java 21).

