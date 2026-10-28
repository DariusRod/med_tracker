# Project-specific R8 rules. Room, Hilt, Navigation and kotlinx.serialization ship their own
# consumer rules, so nothing extra is needed for them here.

# Keep line numbers so release crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
