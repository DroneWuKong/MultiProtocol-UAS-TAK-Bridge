# TAK Bridge - ProGuard rules
# Keep USB serial driver classes
-keep class com.hoho.android.usbserial.** { *; }
# Keep NGA MGRS library
-keep class mil.nga.** { *; }
# Keep OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
