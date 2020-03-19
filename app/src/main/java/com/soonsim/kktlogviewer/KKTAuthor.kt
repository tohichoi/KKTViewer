package com.soonsim.kktlogviewer

import com.stfalcon.chatkit.commons.models.IUser


class KKTAuthor(var authorId:String, var authorAlias:String, var avatarUri:String?) : IUser {

    override fun getId(): String {
        return authorId
    }

    override fun getName(): String {
        return authorAlias
    }

    override fun getAvatar(): String? {
        return avatarUri
    }

    override fun toString() : String {
        return "$authorId($authorAlias)"
    }
}