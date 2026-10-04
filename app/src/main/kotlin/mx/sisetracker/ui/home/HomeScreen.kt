package mx.sisetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.theme.SiseTrackerTheme

@Composable
fun HomeScreen(onSearchClick: () -> Unit) {
    Scaffold(
        topBar = { SiseTopAppBar(title = stringResource(R.string.app_name)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onSearchClick) {
                Text(stringResource(R.string.home_search_button))
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    SiseTrackerTheme {
        HomeScreen(onSearchClick = {})
    }
}
