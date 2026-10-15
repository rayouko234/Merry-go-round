package com.example.merrygoround.core.common

/**
 * Shared client-side validation used by forms AND re-validated by the backend
 * (LocalBackend / Django). The backend remains authoritative.
 */
object Validators {

    private val emailRegex = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""")
    private val phoneRegex = Regex("""^(?:\+?254|0)(7|1)\d{8}$""")
    private val nameRegex = Regex("""^[A-Za-z][A-Za-z .'-]{1,60}$""")

    fun validateFullName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Full name is required"
            trimmed.length < 2 -> "Enter your full name"
            !nameRegex.matches(trimmed) -> "Use letters only (A–Z, spaces, hyphens)"
            else -> null
        }
    }

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Email is required"
            !emailRegex.matches(trimmed) -> "Enter a valid email address"
            else -> null
        }
    }

    /** Accepts Kenyan formats: 07XXXXXXXX, +2547XXXXXXXX, 01XXXXXXXX, +2541XXXXXXXX. */
    fun validatePhone(phone: String): String? {
        val trimmed = phone.trim().replace(" ", "").replace("-", "")
        return when {
            trimmed.isEmpty() -> "Phone number is required"
            !phoneRegex.matches(trimmed) -> "Enter a valid Kenyan phone number (e.g. 0712345678)"
            else -> null
        }
    }

    /** Normalises phone numbers to the +2547XXXXXXXX form for storage/comparison. */
    fun normalizePhone(phone: String): String {
        val digits = phone.trim().replace(" ", "").replace("-", "")
        return when {
            digits.startsWith("+254") -> digits
            digits.startsWith("254") -> "+$digits"
            digits.startsWith("0") -> "+254${digits.substring(1)}"
            else -> digits
        }
    }

    fun validatePassword(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < 8 -> "Password must be at least 8 characters"
        password.length > 128 -> "Password must be at most 128 characters"
        !password.any { it.isDigit() } -> "Password must contain at least one number"
        else -> null
    }

    fun validateGroupName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Group name is required"
            trimmed.length < 3 -> "Group name must be at least 3 characters"
            trimmed.length > 60 -> "Group name must be at most 60 characters"
            else -> null
        }
    }

    fun validateDescription(description: String): String? =
        if (description.length > 300) "Description must be at most 300 characters" else null

    /** Contribution amount in minor units; bounds chosen for KES MVP. */
    fun validateContributionAmount(minor: Long, currency: String = "KES"): String? = when {
        minor <= 0L -> "Contribution amount must be greater than zero"
        minor < 100L -> "Minimum contribution is ${Money.format(100L, currency)}"
        minor > 10_000_000_00L -> "Maximum contribution is ${Money.format(10_000_000_00L, currency)}"
        else -> null
    }

    fun validateMemberLimit(limit: Int): String? = when {
        limit < 2 -> "A group needs at least 2 members"
        limit > 50 -> "Maximum group size is 50 members"
        else -> null
    }

    fun validateStartDate(epochDay: Long, todayEpochDay: Long): String? = when {
        epochDay < todayEpochDay -> "Start date cannot be in the past"
        epochDay > todayEpochDay + 365 -> "Start date must be within the next year"
        else -> null
    }

    fun validateCustomIntervalDays(days: Int): String? = when {
        days < 1 -> "Interval must be at least 1 day"
        days > 90 -> "Interval must be at most 90 days"
        else -> null
    }

    /** Invitation code entry: uppercase letters+digits, I/O/0/1 excluded to avoid ambiguity. */
    fun validateInviteCode(code: String): String? {
        val trimmed = code.trim().uppercase()
        return when {
            trimmed.isEmpty() -> "Enter an invitation code"
            !trimmed.matches(Regex("""^[A-Z2-9]{6,12}$""")) -> "Codes are 6–12 letters/digits (no O, I, 0, 1)"
            else -> null
        }
    }
}
