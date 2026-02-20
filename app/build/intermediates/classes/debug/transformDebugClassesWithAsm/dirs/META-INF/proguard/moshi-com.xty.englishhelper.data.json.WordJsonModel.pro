-keepnames class com.xty.englishhelper.data.json.WordJsonModel
-if class com.xty.englishhelper.data.json.WordJsonModel
-keep class com.xty.englishhelper.data.json.WordJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.WordJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.WordJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,java.util.List,java.lang.String,java.util.List,java.util.List,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
