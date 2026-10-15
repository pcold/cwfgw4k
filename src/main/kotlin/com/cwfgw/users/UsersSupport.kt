package com.cwfgw.users

import com.cwfgw.db.Transactor

/** @see com.cwfgw.AppServices.from */
internal class UsersSupport private constructor(
    val authService: AuthService,
) {
    companion object {
        fun from(
            userRepository: UserRepository,
            transactor: Transactor,
            bcryptCost: Int,
        ): UsersSupport = UsersSupport(authService = AuthService(userRepository, transactor, cost = bcryptCost))
    }
}
