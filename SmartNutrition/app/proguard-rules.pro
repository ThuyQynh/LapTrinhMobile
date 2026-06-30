# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Giữ lại các model class để tránh lỗi parse JSON (Gson)
-keep class com.team.smartnutrition.auth.model.** { *; }
-keep class com.team.smartnutrition.pantry.model.** { *; }
-keep class com.team.smartnutrition.meal.model.** { *; }
-keep class com.team.smartnutrition.habit.model.** { *; }
-keep class com.team.smartnutrition.analytics.model.** { *; }

# Firebase Proguard Rules
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
