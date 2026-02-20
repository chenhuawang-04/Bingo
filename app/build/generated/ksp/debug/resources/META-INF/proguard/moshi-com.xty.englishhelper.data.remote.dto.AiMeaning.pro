-keepnames class com.xty.englishhelper.data.remote.dto.AiMeaning
-if class com.xty.englishhelper.data.remote.dto.AiMeaning
-keep class com.xty.englishhelper.data.remote.dto.AiMeaningJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.remote.dto.AiMeaning
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.remote.dto.AiMeaning {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
