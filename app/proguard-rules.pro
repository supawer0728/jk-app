# jkapp ProGuard/R8 규칙
#
# 현재 release 는 optimization.enable = false 로 최소화(minify)를 끈 상태다.
# 아래 규칙은 이후 R8 최적화를 켤 때 리플렉션 기반 라이브러리(Firestore/Serialization/
# Google API Client)가 깨지지 않도록 미리 정의해 둔 것이다.

# ---- 디버깅용 소스 라인 정보 유지 ----
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---- 도메인 모델: Firestore toObject() 리플렉션 대상 ----
# CatRecord/DailyAsset/DailyAssetInvestment/Benchmark/TodoItem 등 data class 는
# Firestore 가 no-arg 생성자 + 프로퍼티 리플렉션으로 역직렬화한다.
-keepclassmembers class com.jkapp.** {
    <init>();
    <fields>;
}

# ---- kotlinx.serialization ----
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jkapp.**$$serializer { *; }
-keepclassmembers class com.jkapp.** {
    *** Companion;
}

# ---- Firebase / Firestore ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ---- Google API Client (Drive / Sheets) ----
# @Key 어노테이션 필드를 리플렉션으로 매핑한다.
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn javax.naming.**

# ---- gRPC (Firestore 전송 계층) ----
-dontwarn io.grpc.**
-keep class io.grpc.** { *; }
