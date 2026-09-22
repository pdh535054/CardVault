# JNA maps native structures and callbacks through reflection. Removing or renaming
# these members breaks Windows CryptProtectData only in the packaged release build.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn com.sun.jna.**

# Keep the narrow DPAPI adapter and its safe failure type intact as an additional
# release-build guard. Bank-card and address model classes remain eligible for normal
# shrinking and obfuscation.
-keep class com.pdh.cardvault.desktop.data.WindowsDpapiKeyProtector { *; }
-keep class com.pdh.cardvault.desktop.data.LocalKeyUnavailableException { *; }
