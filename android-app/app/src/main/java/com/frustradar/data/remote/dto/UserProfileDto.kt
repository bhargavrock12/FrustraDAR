package com.frustradar.data.remote.dto

/**
 * DTOs for `/api/v1/users` endpoints.
 * Field names verified against backend `routes/users.py`.
 *
 * Profile read uses [UserResponse] from UserDto.
 * Profile update (`PUT /users/profile?username=`) uses a query param, not body.
 */

/** PUT /users/profile — response */
data class ProfileUpdateResponse(
    val message: String,
    val username: String
)

/** DELETE /users/account — response */
data class AccountDeactivateResponse(
    val message: String
)
