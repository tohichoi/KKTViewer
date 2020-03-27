package com.soonsim.kktlogviewer

import com.stfalcon.chatkit.commons.models.IUser
import io.realm.RealmObject


open class KKTAuthor(
    var authorId: String = "Unknown",
    var authorAlias: String = "Unknown",
    var avatarUri: String? = null
) : IUser, RealmObject() {

    constructor(author: KKTAuthor) : this() {
        authorId = author.authorId
        authorAlias = author.authorAlias
        avatarUri = author.avatarUri
    }

    override fun getId(): String {
        return authorId
    }

    override fun getName(): String {
        return authorAlias
    }

    override fun getAvatar(): String? {
        return avatarUri
    }

    override fun toString(): String {
        return "$authorId($authorAlias)"
    }
}