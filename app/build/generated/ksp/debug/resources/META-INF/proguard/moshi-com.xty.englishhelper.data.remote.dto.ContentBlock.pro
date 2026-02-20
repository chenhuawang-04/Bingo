-keepnames class com.xty.englishhelper.data.remote.dto.ContentBlock
-if class com.xty.englishhelper.data.remote.dto.ContentBlock
-keep class com.xty.englishhelper.data.remote.dto.ContentBlockJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.ContentBlock
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.ContentBlock {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
