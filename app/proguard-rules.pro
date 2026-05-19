# SSHJ
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# EdDSA
-keep class net.i2p.crypto.** { *; }
-dontwarn net.i2p.crypto.**

# SLF4J
-dontwarn org.slf4j.**

# Room entities and DAOs
-keep class com.tmuxes.data.model.** { *; }
-keep class com.tmuxes.data.db.** { *; }

# Widget providers and config activity (referenced by XML string, R8 may not trace)
-keep class com.tmuxes.widget.TerminalWidget { *; }
-keep class com.tmuxes.widget.TerminalWidgetSmall { *; }
-keep class com.tmuxes.widget.TerminalWidgetMedium { *; }
-keep class com.tmuxes.widget.TerminalWidgetLarge { *; }
-keep class com.tmuxes.widget.WidgetConfigActivity { *; }

# SnakeYAML (uses Java Beans API not available on Android)
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**

# Sora Editor + tm4e (TextMate grammar/theme engine uses reflection)
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
-dontwarn io.github.rosemoe.sora.**
-dontwarn org.eclipse.tm4e.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**

# JDK internal references
-dontwarn sun.security.x509.X509Key
