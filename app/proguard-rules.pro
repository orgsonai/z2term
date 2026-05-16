# Z2Term ProGuard rules
# M1 段階では minify 無効。Release ビルド時の保護は今後追加。

# JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
