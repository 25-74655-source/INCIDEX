import backend.admin.Admin
import backend.authentication.AuthService
import backend.dashboard.DashboardService
import backend.database.DataStore
import backend.gis.MapPin
import backend.locations.Location
import backend.locations.LocationCalculator
import backend.reports.Report
import backend.verification.VerificationService
import backend.users.User

fun main() {

    println("====================================")
    println("       INCIDEX BACKEND SYSTEM")
    println("====================================")


    // CREATE DATA STORE
    val dataStore = DataStore()

    println("\n[DATABASE]")
    println("DataStore created successfully.")

    // AUTHENTICATION
    val authService = AuthService()

    println("\n[AUTHENTICATION]")

    // Create User
    val user = User(
        userId = "U001",
        name = "Juan Dela Cruz",
        email = "juan@gmail.com",
        password = "123456",
        role = "user"
    )

    // Create Admin
    val admin = Admin(
        adminId = "A001",
        name = "Admin One",
        email = "admin@incidex.com",
        password = "admin123"
    )

    // Register User
    val userRegistered = authService.registerUser(user)

    println("User registered: $userRegistered")

    // Register Admin
    val adminRegistered = authService.registerAdmin(admin)

    println("Admin registered: $adminRegistered")

    // USER LOGIN
    println("\n[USER LOGIN]")

    val userLogin = authService.loginUser(
        email = "juan@gmail.com",
        password = "123456"
    )

    println("Login successful: $userLogin")
    println("Current user: ${authService.getCurrentUser()?.name}")
    println("Current role: ${authService.getCurrentRole()}")

    authService.logout()

    println("User logged out.")
    println("Logged in: ${authService.isLoggedIn()}")


    // ADMIN LOGIN
    println("\n[ADMIN LOGIN]")

    val adminLogin = authService.loginAdmin(
        email = "admin@incidex.com",
        password = "admin123"
    )

    println("Login successful: $adminLogin")
    println("Current admin: ${authService.getCurrentAdmin()?.name}")
    println("Current role: ${authService.getCurrentRole()}")

    authService.logout()

    // CREATE REPORTS
    println("\n[REPORTS]")

    val report1 = Report(
        reportId = "R001",
        reporterId = "U001",
        category = "Road Accident",
        severity = "High",
        description = "Two vehicles collided near the highway.",
        latitude = 14.0668,
        longitude = 120.6327,
        address = "Nasugbu, Batangas",
        photoUrl = "accident001.jpg",
        status = "Submitted"
    )

    val report2 = Report(
        reportId = "R002",
        reporterId = "U001",
        category = "Road Obstruction",
        severity = "Medium",
        description = "A fallen tree is blocking part of the road.",
        latitude = 14.0700,
        longitude = 120.6400,
        address = "Nasugbu, Batangas",
        photoUrl = "obstruction001.jpg",
        status = "Submitted"
    )

    val report3 = Report(
        reportId = "R003",
        reporterId = "U001",
        category = "Flooding",
        severity = "High",
        description = "Heavy flooding reported on the road.",
        latitude = 14.0750,
        longitude = 120.6450,
        address = "Nasugbu, Batangas",
        photoUrl = "flood001.jpg",
        status = "Submitted"
    )

    // Add reports to DataStore
    dataStore.reports.add(report1)
    dataStore.reports.add(report2)
    dataStore.reports.add(report3)

    println("Reports stored: ${dataStore.reports.size}")

    // VERIFICATION
    println("\n[VERIFICATION]")

    val verificationService = VerificationService()

    // Verify Report 1
    val verification1 = verificationService.verifyReport(
        report = report1,
        adminId = "A001",
        notes = "Report details and location were confirmed."
    )

    // Reject Report 2
    val verification2 = verificationService.rejectReport(
        report = report2,
        adminId = "A001",
        notes = "Insufficient information provided."
    )

    // Mark Report 3 as Duplicate
    val verification3 = verificationService.markAsDuplicate(
        report = report3,
        adminId = "A001",
        notes = "This report matches an existing incident."
    )

    // Store verifications
    dataStore.verifications.add(verification1)
    dataStore.verifications.add(verification2)
    dataStore.verifications.add(verification3)

    println("Report ${verification1.reportId}: ${verification1.result}")
    println("Report ${verification2.reportId}: ${verification2.result}")
    println("Report ${verification3.reportId}: ${verification3.result}")

    //  UPDATE REPORT STATUS
    println("\n[REPORT STATUS]")


    val verifiedReport = report1.copy(
        status = "Verified"
    )

    val rejectedReport = report2.copy(
        status = "Rejected"
    )

    val duplicateReport = report3.copy(
        status = "Rejected"
    )

    dataStore.reports.clear()

    dataStore.reports.add(verifiedReport)
    dataStore.reports.add(rejectedReport)
    dataStore.reports.add(duplicateReport)

    println("${verifiedReport.reportId}: ${verifiedReport.status}")
    println("${rejectedReport.reportId}: ${rejectedReport.status}")
    println("${duplicateReport.reportId}: ${duplicateReport.status}")


    // DASHBOARD
    println("\n[DASHBOARD]")

    val dashboardService = DashboardService()

    val totalReports =
        dashboardService.getTotalReports(dataStore.reports)

    val submittedReports =
        dashboardService.getSubmittedReports(dataStore.reports)

    val verifiedReports =
        dashboardService.getVerifiedReports(dataStore.reports)

    val resolvedReports =
        dashboardService.getResolvedReports(dataStore.reports)

    val rejectedReports =
        dashboardService.getRejectedReports(dataStore.reports)

    println("Total reports: $totalReports")
    println("Submitted reports: $submittedReports")
    println("Verified reports: $verifiedReports")
    println("Resolved reports: $resolvedReports")
    println("Rejected reports: $rejectedReports")

    //  REPORTS BY CATEGORY
    println("\n[REPORTS BY CATEGORY]")

    val roadAccidents =
        dashboardService.getReportsByCategory(
            dataStore.reports,
            "Road Accident"
        )

    val roadObstructions =
        dashboardService.getReportsByCategory(
            dataStore.reports,
            "Road Obstruction"
        )

    val flooding =
        dashboardService.getReportsByCategory(
            dataStore.reports,
            "Flooding"
        )

    println("Road Accident: $roadAccidents")
    println("Road Obstruction: $roadObstructions")
    println("Flooding: $flooding")

    // 10. LOCATION
    println("\n[LOCATION]")

    val location1 = Location(
        latitude = 14.0668,
        longitude = 120.6327,
        address = "Nasugbu, Batangas"
    )

    val location2 = Location(
        latitude = 14.0700,
        longitude = 120.6400,
        address = "Nearby Area, Nasugbu"
    )

    println("Location 1: ${location1.address}")
    println("Latitude: ${location1.latitude}")
    println("Longitude: ${location1.longitude}")

    println("\nLocation 2: ${location2.address}")
    println("Latitude: ${location2.latitude}")
    println("Longitude: ${location2.longitude}")

    // CALCULATE DISTANCE
    println("\n[LOCATION DISTANCE]")

    val locationCalculator = LocationCalculator()

    val distance = locationCalculator.calculateDistance(
        location1,
        location2
    )

    println("Distance: %.2f km".format(distance))

    //  GIS MAP PIN
    println("\n[GIS MAP PIN]")

    val mapPin = MapPin(
        incidentId = verifiedReport.reportId,
        latitude = verifiedReport.latitude,
        longitude = verifiedReport.longitude,
        category = verifiedReport.category,
        severity = verifiedReport.severity,
        status = verifiedReport.status
    )

    println("Incident ID: ${mapPin.incidentId}")
    println("Latitude: ${mapPin.latitude}")
    println("Longitude: ${mapPin.longitude}")
    println("Category: ${mapPin.category}")
    println("Severity: ${mapPin.severity}")
    println("Status: ${mapPin.status}")


    println("\n====================================")
    println("       INCIDEX")
    println("====================================")

    println("Users: ${dataStore.users.size}")
    println("Admins: ${dataStore.admins.size}")
    println("Reports: ${dataStore.reports.size}")
    println("Verifications: ${dataStore.verifications.size}")

}