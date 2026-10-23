package me.nomi.urdutyper.domain.repository

import me.nomi.urdutyper.domain.entity.User

interface SharedPreferenceRepository {
    var user: User?
    var email: String
    var password: String
    var tokenId: String
    fun clear()
}