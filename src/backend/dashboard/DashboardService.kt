package backend.dashboard

import backend.reports.Report

class DashboardService {

    fun getTotalReports(reports: List<Report>): Int {
        return reports.size
    }

    fun getSubmittedReports(reports: List<Report>): Int {
        return reports.count {
            it.status.equals("Submitted", ignoreCase = true)
        }
    }

    fun getVerifiedReports(reports: List<Report>): Int {
        return reports.count {
            it.status.equals("Verified", ignoreCase = true)
        }
    }

    fun getResolvedReports(reports: List<Report>): Int {
        return reports.count {
            it.status.equals("Resolved", ignoreCase = true)
        }
    }

    fun getRejectedReports(reports: List<Report>): Int {
        return reports.count {
            it.status.equals("Rejected", ignoreCase = true)
        }
    }

    fun getReportsByCategory(
        reports: List<Report>,
        category: String
    ): Int {
        return reports.count {
            it.category.equals(category, ignoreCase = true)
        }
    }
}