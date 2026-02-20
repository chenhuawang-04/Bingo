-keepnames class com.xty.englishhelper.data.remote.dto.AiCognate
-if class com.xty.englishhelper.data.remote.dto.AiCognate
-keep class com.xty.englishhelper.data.remote.dto.AiCognateJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AiCognate
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AiCognate {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
