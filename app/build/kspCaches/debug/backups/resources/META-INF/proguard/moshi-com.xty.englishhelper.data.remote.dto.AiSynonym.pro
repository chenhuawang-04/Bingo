-keepnames class com.xty.englishhelper.data.remote.dto.AiSynonym
-if class com.xty.englishhelper.data.remote.dto.AiSynonym
-keep class com.xty.englishhelper.data.remote.dto.AiSynonymJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AiSynonym
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AiSynonym {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
