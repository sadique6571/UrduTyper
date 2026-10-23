package me.nomi.urdutyper.domain.entity

data class User(
    val id: String = "",
    val email: String? = "",
    val name: String? = "",
    val photoUrl: String?  = ""
)