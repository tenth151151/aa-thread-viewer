package io.github.tenth151151.aathreadviewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.tenth151151.aathreadviewer.home.HomeKey
import io.github.tenth151151.aathreadviewer.home.HomeScreen
import io.github.tenth151151.aathreadviewer.thread.ThreadKey
import io.github.tenth151151.aathreadviewer.thread.ThreadScreen

@Composable
fun AatvNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val openDat =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            // 選ぶのをやめたときは null で、ホームのままにする。
            if (uri != null) {
                backStack.add(ThreadKey(uri.toString()))
            }
        }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider =
            entryProvider {
                entry<HomeKey> {
                    HomeScreen(
                        onOpenDat = {
                            // dat には決まった MIME タイプがなく、ファイルを渡すアプリによって値が変わるので、すべてのファイルを選べるようにする。
                            openDat.launch(arrayOf("*/*"))
                        },
                    )
                }
                entry<ThreadKey> { key ->
                    ThreadScreen(uri = key.uri)
                }
            },
    )
}
