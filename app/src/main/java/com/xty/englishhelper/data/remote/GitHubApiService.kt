package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.GitHubContentResponse
import com.xty.englishhelper.data.remote.dto.GitHubDeleteRequest
import com.xty.englishhelper.data.remote.dto.GitHubPutRequest
import com.xty.englishhelper.data.remote.dto.GitHubPutResponse
import com.xty.englishhelper.data.remote.dto.GitHubRepoResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

interface GitHubApiService {

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepoResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String
    ): Response<GitHubContentResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun listDirectory(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String
    ): Response<List<GitHubContentResponse>>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: GitHubPutRequest
    ): Response<GitHubPutResponse>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: GitHubDeleteRequest
    ): Response<Unit>

    @GET
    suspend fun downloadRaw(
        @Header("Authorization") auth: String,
        @Url url: String
    ): Response<ResponseBody>
}
