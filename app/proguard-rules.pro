-keepattributes Signature
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class cn.edu.xatu.helper.model.** { *; }
# Preserve the encrypted session JSON contract across debug/release updates.
-keep class cn.edu.xatu.helper.data.Credentials { *; }
-keep class cn.edu.xatu.helper.data.CookieRecord { *; }
-keep class cn.edu.xatu.helper.data.Secrets { *; }
