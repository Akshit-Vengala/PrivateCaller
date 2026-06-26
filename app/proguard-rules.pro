# Keep Room generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep our Service entry points referenced from the manifest
-keep class com.privatecaller.service.** { *; }
