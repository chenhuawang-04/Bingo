-keepnames class com.xty.englishhelper.data.json.DictionaryJsonModel
-if class com.xty.englishhelper.data.json.DictionaryJsonModel
-keep class com.xty.englishhelper.data.json.DictionaryJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.DictionaryJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.DictionaryJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
