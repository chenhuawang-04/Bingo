-keepnames class com.xty.englishhelper.data.remote.dto.MessageDto
-if class com.xty.englishhelper.data.remote.dto.MessageDto
-keep class com.xty.englishhelper.data.remote.dto.MessageDtoJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
