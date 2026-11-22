# HeaRt companion

Platform: android. Kotlin and Jetpack Compose, reusing the existing ET488/Veepoo and Q520/Yucheng device adapters and UI primitives from the user's Vigil project. An independent application ID and sandbox keep accounts, files and device registrations separate from Vigil.

Four main destinations: Home, Devices, History and Files. Sign-in and registration use the existing FastAPI cookie-auth API. The native visual system uses crimson, warm white and a dimensional anatomical heart. Connection is passive. Read heart rate takes one measurement and stops; saving a recording explicitly enables repeated measurements until Stop; foreground-service notification remains visible. Only supported protocol adapters are offered, not universal watch compatibility. Device/transport failure must be visible. Persistent backend readings and local CSV recovery must be available.

The user's instructions authorise implementation without questions, reuse installed SDK/build tooling, test on the connected phone at the end, with commits only when requested and without co-author trailers. Do not copy user credentials, databases or Vigil clinic settings.
