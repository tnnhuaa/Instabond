package com.example.instabond_fe.data.remote

import com.example.instabond_fe.data.model.ChatMessageResponse
import com.example.instabond_fe.data.model.Conversation
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApiService {
    @POST("api/conversations/direct")
    suspend fun getOrCreateDirectConversation(
        @Query("partnerId") partnerId: String
    ): Response<Conversation>

    @GET("api/messages/conversation/{conversationId}/history")
    suspend fun getHistory(
        @Path("conversationId") conversationId: String,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<List<ChatMessageResponse>>

    @Multipart
    @POST("api/messages/conversation/{conversationId}/images")
    suspend fun uploadChatImage(
        @Path("conversationId") conversationId: String,
        @Part file: MultipartBody.Part
    ): Response<ChatMessageResponse>

    @POST("api/messages/conversation/{conversationId}/read")
    suspend fun markMessagesAsRead(
        @Path("conversationId") conversationId: String
    ): Response<Unit>
}

