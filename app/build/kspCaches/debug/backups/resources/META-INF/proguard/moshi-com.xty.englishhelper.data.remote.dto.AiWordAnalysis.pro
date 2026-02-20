-keepnames class com.xty.englishhelper.data.remote.dto.AiWordAnalysis
-if class com.xty.englishhelper.data.remote.dto.AiWordAnalysis
-keep class com.xty.englishhelper.data.remote.dto.AiWordAnalysisJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AiWordAnalysis
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AiWordAnalysis {
    public synthetic <init>(java.lang.String,java.util.List,java.lang.String,java.util.List,java.util.List,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
