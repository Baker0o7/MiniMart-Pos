package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.UserDao
import com.minimart.pos.data.entity.User
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val dao: UserDao) {

    fun getAllUsers(): Flow<List<User>> = dao.getAllUsers()

    suspend fun getUserById(id: Long): User? = dao.getUserById(id)

    suspend fun getUserByUsername(username: String): User? = dao.getUserByUsername(username)

    suspend fun login(username: String, pin: String): User? =
        dao.login(username.trim(), sha256(pin.trim()))

    suspend fun insertUser(user: User): Long = dao.insertUser(user)

    suspend fun updateUser(user: User) = dao.updateUser(user)

    suspend fun deleteUser(user: User) = dao.deleteUser(user)

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
