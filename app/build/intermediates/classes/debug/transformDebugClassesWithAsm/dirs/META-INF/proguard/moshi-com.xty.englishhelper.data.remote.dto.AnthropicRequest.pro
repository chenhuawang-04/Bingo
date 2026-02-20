-keepnames class com.xty.englishhelper.data.remote.dto.AnthropicRequest
-if class com.xty.englishhelper.data.remote.dto.AnthropicRequest
-keep class com.xty.englishhelper.data.remote.dto.AnthropicRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AnthropicRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AnthropicRequest {
    public synthetic <init>(java.lang.String,int,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
