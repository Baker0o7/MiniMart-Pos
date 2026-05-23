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
    suspend fun getUserCount(): Int = dao.getUserCount()
    suspend fun insertUser(user: User): Long = dao.insertUser(user)
    suspend fun updateUser(user: User) = dao.updateUser(user)
    suspend fun deleteUser(user: User) = dao.deleteUser(user)

    suspend fun login(username: String, pin: String): User? {
        val user = dao.getUserByUsername(username.trim()) ?: return null
        return if (sha256(pin.trim()) == user.pinHash) user else null
    }

    /** Login using PinHasher — supports both Argon2id and legacy SHA-256 */
    suspend fun loginWithHasher(
        username: String, pin: String,
        hasher: com.minimart.pos.util.PinHasher
    ): User? {
        val user = dao.getUserByUsername(username.trim()) ?: return null
        return if (hasher.verify(pin.trim(), user.pinHash)) user else null
    }

    /** Upgrade a user's stored hash to Argon2id after successful login */
    suspend fun upgradePinHash(userId: Long, newHash: String) {
        val user = dao.getUserById(userId) ?: return
        dao.updateUser(user.copy(pinHash = newHash))
    }

    /** Hash a new PIN using Argon2id via PinHasher */
    fun hashPin(pin: String, hasher: com.minimart.pos.util.PinHasher): String =
        hasher.hash(pin)

    fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
