package com.soonsim.kktlogviewer
import com.stfalcon.chatkit.commons.models.IMessage
import java.util.*


class KKTMessage(var messageId:String?=null, var messageText:String?=null, var author:KKTAuthor?=null, var messageTime:Date?=null) : IMessage {

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

    override fun toString() : String {
        return "$messageTime, $author : $messageText\n"
    }

    companion object {
        var messageIndex=-1

        fun random() : KKTMessage {
            val pairList=listOf<Pair<String, String>>(
                Pair<String, String>("John", "John Doe"),
                Pair<String, String>("Jane", "Jane Doe"),
                Pair<String, String>("God", "Zeus")
            )

            val author=pairList.random()
            messageIndex+=1

            return KKTMessage(
                messageIndex.toString(),
                "chat message: $messageIndex",
                KKTAuthor(author.first, author.second, null), Date(System.currentTimeMillis()))
        }
    }
}