-keepattributes Signature
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class com.xatune.app.model.** { *; }
# Preserve the encrypted session JSON contract across debug/release updates.
-keep class com.xatune.app.data.Credentials { *; }
-keep class com.xatune.app.data.CookieRecord { *; }
-keep class com.xatune.app.data.Secrets { *; }
# Keep the local update-cache JSON stable across optimized app updates.
-keep class com.xatune.app.data.AppRelease { *; }
-keep class com.xatune.app.data.UpdateCache { *; }
