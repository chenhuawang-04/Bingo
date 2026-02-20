-keepnames class com.xty.englishhelper.data.remote.dto.AiSimilarWord
-if class com.xty.englishhelper.data.remote.dto.AiSimilarWord
-keep class com.xty.englishhelper.data.remote.dto.AiSimilarWordJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AiSimilarWord
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AiSimilarWord {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
