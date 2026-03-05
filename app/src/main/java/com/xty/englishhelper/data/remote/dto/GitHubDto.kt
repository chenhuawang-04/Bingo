package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubContentResponse(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val size: Long = 0,
    val type: String = "",
    val content: String? = null,
    val encoding: String? = null,
    @Json(name = "download_url")
    val downloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubPutRequest(
    val message: String,
    val content: String,
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubPutResponse(
    val content: GitHubContentResponse? = null
)

@JsonClass(generateAdapter = true)
data class GitHubDeleteRequest(
    val message: String,
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitHubRepoResponse(
    val id: Long = 0,
    val name: String = "",
    @Json(name = "full_name")
    val fullName: String = "",
    val private: Boolean = false
)
