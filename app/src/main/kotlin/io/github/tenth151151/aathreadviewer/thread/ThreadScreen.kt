package io.github.tenth151151.aathreadviewer.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tenth151151.aathreadviewer.R
import io.github.tenth151151.aathreadviewer.dat.Post
import io.github.tenth151151.aathreadviewer.ui.theme.AAThreadViewerTheme

@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ThreadContent(uiState = uiState, modifier = modifier)
}

@Composable
fun ThreadContent(
    uiState: ThreadUiState,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        ThreadUiState.Loading -> CenteredMessage(stringResource(R.string.thread_loading), modifier)
        ThreadUiState.Error -> CenteredMessage(stringResource(R.string.thread_error), modifier)
        is ThreadUiState.Success -> PostList(uiState, modifier)
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Text(text = message)
    }
}

@Composable
private fun PostList(
    uiState: ThreadUiState.Success,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.title?.let { title ->
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(uiState.posts, key = { it.number }) { post ->
            PostItem(post)
        }
    }
}

@Composable
private fun PostItem(
    post: Post,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when (post) {
            is Post.Readable -> {
                Text(
                    text = stringResource(R.string.thread_post_header, post.number, post.name, post.dateAndId),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = post.body)
            }

            is Post.Unreadable -> {
                Text(text = stringResource(R.string.thread_unreadable_post, post.number))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreadContentPreview() {
    AAThreadViewerTheme {
        ThreadContent(
            uiState =
                ThreadUiState.Success(
                    title = "Sample thread",
                    posts =
                        listOf(
                            Post.Readable(1, "Name", "", "2026/11/08 12:00:00 ID:abcd1234", "Body line 1\nBody line 2"),
                            Post.Unreadable(2),
                        ),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreadContentLoadingPreview() {
    AAThreadViewerTheme {
        ThreadContent(uiState = ThreadUiState.Loading)
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreadContentErrorPreview() {
    AAThreadViewerTheme {
        ThreadContent(uiState = ThreadUiState.Error)
    }
}
