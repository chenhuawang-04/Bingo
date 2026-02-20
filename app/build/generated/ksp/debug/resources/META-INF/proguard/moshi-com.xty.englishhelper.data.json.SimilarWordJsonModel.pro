-keepnames class com.xty.englishhelper.data.json.SimilarWordJsonModel
-if class com.xty.englishhelper.data.json.SimilarWordJsonModel
-keep class com.xty.englishhelper.data.json.SimilarWordJsonModelJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.xty.englishhelper.data.json.SimilarWordJsonModel
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class com.xty.englishhelper.data.json.SimilarWordJsonModel {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
