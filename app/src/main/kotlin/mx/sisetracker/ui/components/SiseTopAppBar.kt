package mx.sisetracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sisetracker.ui.theme.LocalBrandColors

/**
 * Top app bar in deep guinda with white content, over a thin gold rule (see
 * [LocalBrandColors]). It draws behind the status bar, so the app stays
 * edge-to-edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiseTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val brand = LocalBrandColors.current
    Column(modifier) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = brand.topBar,
                scrolledContainerColor = brand.topBar,
                titleContentColor = brand.onTopBar,
                navigationIconContentColor = brand.onTopBar,
                actionIconContentColor = brand.onTopBar,
            ),
        )
        HorizontalDivider(thickness = 3.dp, color = brand.goldRule)
    }
}
