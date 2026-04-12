
)
    val updatedAt: String?
    @SerializedName("updated_at")
    val theme: String?,
    @SerializedName("theme")
    val lastMessage: LastMessage?,
    @SerializedName("last_message")
    val participants: List<String>?,
    @SerializedName("participants")
    val id: String?,
    @SerializedName("id")
data class Conversation(

import com.google.gson.annotations.SerializedName

