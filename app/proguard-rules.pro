# Keep release stack traces readable in crash reports without uploading R8 mapping files.
# Shrinking and optimization remain enabled.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
