# VeltraX 4.1 — keep Media3 / WorkManager essentials if minify is ever enabled
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.work.** { *; }
