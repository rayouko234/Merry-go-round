package com.example.merrygoround.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.components.SecondaryButton
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.BrandGreenLight
import com.example.merrygoround.ui.theme.SurfaceGray
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/**
 * 01 — Welcome. Refined version of the original welcome screen:
 * logo + tagline, three feature cards, Get Started + Login actions.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(BrandGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Savings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Merry-Go-Round",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Merry Go Round" }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Save Together, Build Your Group, Receive Your Share",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        FeatureCard(
            icon = Icons.Filled.Savings,
            title = "Save Together",
            body = "Contribute a fixed amount every week or month with people you trust and watch the pot grow."
        )
        Spacer(Modifier.height(14.dp))
        FeatureCard(
            icon = Icons.Filled.Group,
            title = "Build Your Group",
            body = "Create a savings group, invite members with a code, and set clear rules everyone can see."
        )
        Spacer(Modifier.height(14.dp))
        FeatureCard(
            icon = Icons.Filled.Payments,
            title = "Receive Your Share",
            body = "A transparent rotating payout schedule shows exactly who receives, and when."
        )

        Spacer(Modifier.height(40.dp))

        PrimaryButton(
            text = "Get Started",
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("welcome_get_started")
                .semantics { contentDescription = "Get Started button" }
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "I already have an account",
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("welcome_login")
                .semantics { contentDescription = "Login button" }
        )

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Contributions are tracked, not processed — no real money moves in this version.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceGray,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(BrandGreenLight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

