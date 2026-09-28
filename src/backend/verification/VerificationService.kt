package backend.verification

import backend.reports.Report

class VerificationService {

    fun verifyReport(
        report: Report,
        adminId: String,
        notes: String = ""
    ): Verification {

        return Verification(
            verificationId = generateVerificationId(),
            reportId = report.reportId,
            adminId = adminId,
            result = "Verified",
            notes = notes
        )
    }

    fun rejectReport(
        report: Report,
        adminId: String,
        notes: String = ""
    ): Verification {

        return Verification(
            verificationId = generateVerificationId(),
            reportId = report.reportId,
            adminId = adminId,
            result = "Rejected",
            notes = notes
        )
    }

    fun markAsDuplicate(
        report: Report,
        adminId: String,
        notes: String = ""
    ): Verification {

        return Verification(
            verificationId = generateVerificationId(),
            reportId = report.reportId,
            adminId = adminId,
            result = "Duplicate",
            notes = notes
        )
    }

    private fun generateVerificationId(): String {
        return "V" + System.currentTimeMillis()
    }
}