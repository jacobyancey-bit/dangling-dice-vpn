# Official WireGuard JNI & Native Tunnel Keep Rules
-keep class com.wireguard.android.** { *; }
-keep class com.wireguard.config.** { *; }
-keep class com.wireguard.crypto.** { *; }
-dontwarn com.wireguard.**

# RevenueCat Subscriptions & Paywalls
-keep class com.revenuecat.purchases.Purchases** { *; }
-keep class com.revenuecat.purchases.models.** { *; }
-dontwarn com.revenuecat.purchases.**

# App Data Models & State Classes
-keep class com.danglingdice.vpn.data.model.** { *; }

# OkHttp & Okio Cryptographic Networking
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines & Jetpack Compose
-dontwarn kotlinx.coroutines.**
