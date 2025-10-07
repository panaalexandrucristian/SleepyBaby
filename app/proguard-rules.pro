# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }

# Keep model input/output signatures
-keepclassmembers class com.sleepybaby.core.ai.** {
    public <methods>;
}