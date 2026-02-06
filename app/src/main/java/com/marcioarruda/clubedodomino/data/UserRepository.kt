package com.marcioarruda.clubedodomino.data

// Mock Repository for Users
class UserRepository {

    private val users = listOf(
        User(id = "1", name = "Márcio Arruda", displayName = "Marcio", photoUrl = "url1", clubId = "c1", isMember = true),
        User(id = "2", name = "Leo K.", displayName = "Leo", photoUrl = "url2", clubId = "c1", isMember = true),
        User(id = "3", name = "Ana", displayName = "Ana", photoUrl = "url3", clubId = "c1", isMember = true),
        User(id = "4", name = "Chris", displayName = "Chris", photoUrl = "url4", clubId = "c1", isMember = true),
        User(id = "5", name = "Sam", displayName = "Sam", photoUrl = "url5", clubId = "c1", isMember = true),
        User(id = "6", name = "Visitante 1", displayName = "Visitante", photoUrl = "url6", clubId = "c1", isMember = false) // Non-member
    )

    fun getUsers(): List<User> = users

    fun getUserById(id: String): User? = users.find { it.id == id }
}
