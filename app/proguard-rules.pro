# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep model input/output signatures for the classifier layer
-keepclassmembers class ro.pana.sleepybaby.core.ai.** {
    public <methods>;
}
