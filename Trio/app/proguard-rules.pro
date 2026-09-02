# Room generates classes referenced only by reflection at runtime.
-keep class androidx.room.RoomDatabase { *; }

# Glance/AppWidget receivers are instantiated by the system from the manifest.
-keep class com.trio.today.widget.** { *; }

# Alarm and boot receivers are instantiated by the system.
-keep class com.trio.today.reminder.** { *; }
