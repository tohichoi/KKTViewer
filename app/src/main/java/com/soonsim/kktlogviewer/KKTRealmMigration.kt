package com.soonsim.kktlogviewer

import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration

class KKTRealmMigration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var oldVersion = oldVersion

        // DynamicRealm exposes an editable schema
        val schema = realm.schema

        // Migrate to version 1: Add a new class.
        // Example:
        // open class Person(
        //     var name: String = "",
        //     var age: Int = 0,
        // ): RealmObject()
        if (oldVersion == 0L) {
            schema.create("KKTAuthor")
                .addField("authorId", String::class.java)
                .addField("authorAlias", String::class.java)
                .addField("avatarUri", String::class.java)

            schema.create("KKTMessage")
                .addField("messageId", String::class.java)
                .addField("messageText", String::class.java)
                .addField("author", KKTAuthor::class.java)
                .addField("messageTime", String::class.java)
                .addField("imgUrl", String::class.java)
            oldVersion++
        }

        // Migrate to version 2: Add a primary key + object references
        // Example:
        // open class Person(
        //     var name: String = "",
        //     var age: Int = 0,
        //     @PrimaryKey
        //     var id: Int = 0,
        //     var favoriteDog: Dog? = null,
        //     var dogs: RealmList<Dog> = RealmList()
        // ): RealmObject()

        if (oldVersion == 1L) {
            schema.get("KKTAuthor")!!
                .addField("id", Long::class.javaPrimitiveType, FieldAttribute.PRIMARY_KEY)
                .addRealmObjectField("favoriteDog", schema.get("Dog"))
                .addRealmListField("dogs", schema.get("Dog"))
            oldVersion++
        }
    }
}