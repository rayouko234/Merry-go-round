package com.example.merrygoround.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.ContributionVerification
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.domain.model.PayoutStatus
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.ErrorRed
import com.example.merrygoround.ui.theme.ErrorRedLight
import com.example.merrygoround.ui.theme.InfoBlue
import com.example.merrygoround.ui.theme.InfoBlueLight
import com.example.merrygoround.ui.theme.SurfaceGray
import com.example.merrygoround.ui.theme.TextSecondary
import com.example.merrygoround.ui.theme.WarningAmber
import com.example.merrygoround.ui.theme.WarningAmberLight

/** Primary green CTA with built-in loading/disabled handling (no double submits). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = BrandGreen)
    }
}

enum class ChipTone { SUCCESS, PENDING, ERROR, INFO, NEUTRAL }

/** Status chip used for contribution/payout/membership states. */
@Composable
fun StatusChip(label: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        when (tone) {
            ChipTone.SUCCESS -> BrandGreen.copy(alpha = 0.12f)
            ChipTone.PENDING -> WarningAmberLight
            ChipTone.ERROR -> ErrorRedLight
            ChipTone.INFO -> InfoBlueLight
            ChipTone.NEUTRAL -> SurfaceGray
        }, label = "chipColor"
    )
    val fg = when (tone) {
        ChipTone.SUCCESS -> BrandGreen
        ChipTone.PENDING -> WarningAmber
        ChipTone.ERROR -> ErrorRed
        ChipTone.INFO -> InfoBlue
        ChipTone.NEUTRAL -> TextSecondary
    }
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

fun ContributionStatus.tone(): ChipTone = when (this) {
    ContributionStatus.CONFIRMED -> ChipTone.SUCCESS
    ContributionStatus.PENDING -> ChipTone.PENDING
    ContributionStatus.FAILED -> ChipTone.ERROR
    ContributionStatus.REVERSED -> ChipTone.NEUTRAL
}

fun PayoutStatus.tone(): ChipTone = when (this) {
    PayoutStatus.COMPLETED -> ChipTone.SUCCESS
    PayoutStatus.PENDING -> ChipTone.PENDING
    PayoutStatus.SCHEDULED -> ChipTone.INFO
    PayoutStatus.FAILED -> ChipTone.ERROR
}

fun GroupStatus.label(): String = when (this) {
    GroupStatus.FORMING -> "Forming"
    GroupStatus.ACTIVE -> "Active"
    GroupStatus.PAUSED -> "Paused"
    GroupStatus.COMPLETED -> "Completed"
}

fun MembershipStatus.label(): String = when (this) {
    MembershipStatus.PENDING -> "Pending approval"
    MembershipStatus.ACTIVE -> "Active"
    MembershipStatus.REJECTED -> "Rejected"
    MembershipStatus.REMOVED -> "Removed"
}

/** Verification label — self-reported entries are never shown as verified money. */
@Composable
fun VerificationBadge(verification: ContributionVerification) {
    when (verification) {
        ContributionVerification.SELF_REPORTED -> StatusChip("Self-reported", ChipTone.PENDING)
        ContributionVerification.ADMIN_VERIFIED -> StatusChip("Admin verified", ChipTone.SUCCESS)
        ContributionVerification.PROVIDER_VERIFIED -> StatusChip("Provider verified", ChipTone.INFO)
    }
}
