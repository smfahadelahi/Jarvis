# MediaPipe GenAI ships native libs and Java reflection surfaces.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Picovoice Porcupine wake word engine.
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# org.json is provided by the Android runtime.
-dontwarn org.json.**
