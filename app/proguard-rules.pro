# Tesseract4Android's native code calls back into these classes by name (JNI), and the library
# ships no consumer rules of its own.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
