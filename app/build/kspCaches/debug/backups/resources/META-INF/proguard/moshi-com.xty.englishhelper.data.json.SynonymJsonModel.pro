-keepnames class com.xty.englishhelper.data.json.SynonymJsonModel
-if class com.xty.englishhelper.data.json.SynonymJsonModel
-keep class com.xty.englishhelper.data.json.SynonymJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.SynonymJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.SynonymJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
