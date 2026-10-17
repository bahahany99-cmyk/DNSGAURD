# DNS Guardian — ProGuard rules for release build.

# Keep Flutter platform channel method names (reflection-free but safe).
-keep class com.dnsguardian.app.** { *; }

# Keep security-critical classes.
-keep class com.dnsguardian.app.security.** { *; }
-keep class com.dnsguardian.app.services.** { *; }
-keep class com.dnsguardian.app.platform.** { *; }

# AndroidX security crypto.
-keep class androidx.security.crypto.** { *; }

# Allow AccessibilityService to be instantiated by the system.
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.app.admin.DeviceAdminReceiver
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep serialized event model fields.
-keepclassmembers class com.dnsguardian.app.security.** { *; }
