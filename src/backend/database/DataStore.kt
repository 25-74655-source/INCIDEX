package backend.database

import backend.admin.Admin
import backend.reports.Report
import backend.users.User
import backend.verification.Verification

class DataStore {

    val users = mutableListOf<User>()

    val admins = mutableListOf<Admin>()

    val reports = mutableListOf<Report>()

    val verifications = mutableListOf<Verification>()
}