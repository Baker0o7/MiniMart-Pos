package com.minimart.pos.data

import android.database.sqlite.SQLiteConstraintException
import com.minimart.pos.data.entity.User
import com.minimart.pos.data.entity.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class UserDaoTest : DbTestBase() {
    private fun user(name: String) = User(username = name, pinHash = "hash", displayName = name, role = UserRole.CASHIER)

    @Test fun usernamesAreUnique() = runBlocking {
        db.userDao().insertUser(user("amina"))
        try {
            db.userDao().insertUser(user("amina"))
            fail("duplicate usernames must be rejected")
        } catch (_: SQLiteConstraintException) { }
        assertEquals(1, db.userDao().getUserCount())
    }

    @Test fun lookupByUsernameFindsTheRightUser() = runBlocking {
        db.userDao().insertUser(user("juma"))
        assertEquals("juma", db.userDao().getUserByUsername("juma")?.username)
        assertNull(db.userDao().getUserByUsername("nobody"))
    }
}
