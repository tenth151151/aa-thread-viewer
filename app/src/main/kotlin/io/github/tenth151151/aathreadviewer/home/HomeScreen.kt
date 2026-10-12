package io.github.tenth151151.aathreadviewer.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.tenth151151.aathreadviewer.R
import io.github.tenth151151.aathreadviewer.ui.theme.AAThreadViewerTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.app_name))
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AAThreadViewerTheme {
        HomeScreen()
    }
}
