-keepnames class com.xty.englishhelper.data.remote.dto.AnthropicResponse
-if class com.xty.englishhelper.data.remote.dto.AnthropicResponse
-keep class com.xty.englishhelper.data.remote.dto.AnthropicResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AnthropicResponse
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AnthropicResponse {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
