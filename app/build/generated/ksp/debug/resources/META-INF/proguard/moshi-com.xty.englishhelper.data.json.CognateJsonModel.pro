-keepnames class com.xty.englishhelper.data.json.CognateJsonModel
-if class com.xty.englishhelper.data.json.CognateJsonModel
-keep class com.xty.englishhelper.data.json.CognateJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.CognateJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.CognateJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
