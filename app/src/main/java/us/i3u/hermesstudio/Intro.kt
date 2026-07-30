package us.i3u.hermesstudio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shown while a stored session is being verified. Deliberately has no fields:
 * seeing the sign-in form on every launch reads as "your session is gone".
 */
@Composable
fun LoadingScreen(baseUrl: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark()
            Spacer(Modifier.height(24.dp))
            Text("Hermes Studio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                baseUrl.ifBlank { "Restoring your session…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "H",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class IntroPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val points: List<String> = emptyList(),
)

private val INTRO_PAGES = listOf(
    IntroPage(
        icon = Icons.Filled.Chat,
        title = "Your agents, on your phone",
        body = "A native client for Hermes Studio. Read your conversations, keep talking to your agents, attach photos and files, and dictate with your voice.",
    ),
    IntroPage(
        icon = Icons.Filled.Dns,
        title = "You bring the server",
        body = "This app is only the front end. It needs a Hermes Studio server that you run and control — there is no hosted service behind it.",
        points = listOf(
            "Install Hermes Studio on a machine or VPS you own",
            "Make it reachable over HTTPS, for example https://hermes.example.com",
            "Come back here with that address and your login",
        ),
    ),
    IntroPage(
        icon = Icons.Filled.Lock,
        title = "Nothing leaves your server",
        body = "Your address, token and messages go only to the server you enter. The token is stored encrypted on this device, and the app carries no analytics.",
    ),
)

/** First-run walkthrough: what the app is, and that a server is required. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { INTRO_PAGES.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == INTRO_PAGES.lastIndex

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    TextButton(onClick = onDone) { Text("Skip") }
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                val page = INTRO_PAGES[index]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (page.points.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            page.points.forEachIndexed { position, point ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${position + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        point,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                INTRO_PAGES.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    val alpha by animateFloatAsState(if (active) 1f else 0.28f, label = "dot")
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .alpha(alpha)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }

            Button(
                onClick = {
                    if (isLast) onDone()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLast) "Connect my server" else "Next")
            }
        }
    }
}
