# KeySwiper release rules

# LiteRT-LM 0.17.x currently does not ship complete consumer keep rules for all
# reflected/JNI entry points. Keep its public/runtime classes in minified builds.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }

# ML Kit models use their own consumer rules; keep our thin integration classes.
-keep class cloud.kosch.keyswiper.prediction.LiteRtLmPredictionBackend { *; }
-keep class cloud.kosch.keyswiper.prediction.NeuralModelManager { *; }
