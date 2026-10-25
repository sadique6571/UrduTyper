package me.nomi.urdutyper.data.mapper

import com.google.firebase.auth.FirebaseUser
import me.nomi.urdutyper.domain.entity.User

object Mapper {
    fun FirebaseUser.toDomain(): User {
        return User(
            id = uid,
            email = email ?: "",
            name = displayName ?: "",
            photoUrl = photoUrl?.toString()
        )
    }
}