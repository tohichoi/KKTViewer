package com.soonsim.kktlogviewer

import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.MessageContentType
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*


open class KKTMessage(
    @PrimaryKey
    var messageId: String? = null,
    var messageText: String? = null,
    var author: KKTAuthor? = null,
    var messageTime: Date? = null,
    var selected: Boolean = false,
    var imgUrl: String? = null
) : IMessage, MessageContentType.Image, RealmObject() {

    constructor(message: KKTMessage) : this() {
        messageId = message.messageId
        messageText = message.messageText
        author = message.author
        messageTime = message.messageTime
        selected = message.selected
        imgUrl = message.imgUrl
    }

    override fun getId(): String? {
        return messageId
    }

    override fun getText(): String? {
        return messageText
    }

    override fun getUser(): KKTAuthor? {
        return author
    }

    override fun getCreatedAt(): Date? {
        return messageTime
    }

    override fun toString(): String {
        return "$messageTime, $author : $messageText\n"
    }

    override fun getImageUrl(): String? {
        return imgUrl
    }


    fun isNull(): Boolean {
        return messageId == null || messageTime == null
    }

    operator fun compareTo(id: String): Int {
        return messageId!!.compareTo(id)
    }

    companion object {
        var messageIndex = -1

        fun random(): KKTMessage {
            val pairList = listOf<Pair<String, String>>(
                Pair<String, String>("John", "John Doe"),
                Pair<String, String>("Jane", "Jane Doe"),
                Pair<String, String>("God", "Zeus")
            )

            val author = pairList.random()
            messageIndex += 1

            return KKTMessage(
                messageIndex.toString(),
                "chat message: $messageIndex",
                KKTAuthor(author.first, author.second, null), Date(System.currentTimeMillis())
            )
        }
    }
}