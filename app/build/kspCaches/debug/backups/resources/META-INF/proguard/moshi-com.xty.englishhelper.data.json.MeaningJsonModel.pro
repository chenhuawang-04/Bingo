-keepnames class com.xty.englishhelper.data.json.MeaningJsonModel
-if class com.xty.englishhelper.data.json.MeaningJsonModel
-keep class com.xty.englishhelper.data.json.MeaningJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.MeaningJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.MeaningJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
