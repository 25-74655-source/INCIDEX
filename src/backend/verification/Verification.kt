package backend.verification

data class Verification(
    val verificationId: String = "",
    val reportId: String = "",
    val adminId: String = "",

    val result: String = "",

    val notes: String = "",

    val verifiedAt: Long = System.currentTimeMillis()
)