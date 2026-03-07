package io.github.a13e300.ksuwebui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen() {
    val viewModel: WebUIViewModel = viewModel(viewModelStoreOwner = App.instance)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val status by viewModel.moduleListStatus.collectAsState()
    val moduleList by viewModel.moduleList.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshModuleListWithStatus
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->

        val statusWithoutUpdating by remember { derivedStateOf { if (status == WebUIViewModel.Status.Updating) WebUIViewModel.Status.Ready else status } }

        Crossfade(
            targetState = statusWithoutUpdating
        ) { currentState ->
            when (currentState) {
                WebUIViewModel.Status.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                WebUIViewModel.Status.Unavailable -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.please_grant_root),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                }

                WebUIViewModel.Status.Updating -> error("We should never reach here because we treat Updating as Ready in the UI")

                WebUIViewModel.Status.Ready -> {
                    val listIsEmpty by remember { derivedStateOf { moduleList.isEmpty() } }

                    Crossfade(
                        targetState = listIsEmpty
                    ) { isEmpty ->
                        if (isEmpty) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_modules),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = innerPadding + PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(moduleList) { module ->
                                    ModuleCard(module = module, modifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
            }
        }

        val isUpdating by remember { derivedStateOf { status == WebUIViewModel.Status.Updating } }

        Crossfade(
            targetState = isUpdating,
            modifier = Modifier.padding(innerPadding)
        ) { isUpdating ->
            if (isUpdating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ModuleCard(
    module: WebUIViewModel.Module,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(context, WebUIActivity::class.java)
                            .setData("ksuwebui://webui/${module.id}".toUri())
                            .putExtra("id", module.id)
                            .putExtra("name", module.name)
                    )
                }
                .padding(16.dp)
        ) {
            Text(
                text = module.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(
                modifier = Modifier.height(2.dp)
            )
            Text(
                text = stringResource(R.string.author, module.author),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.version, module.version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = module.desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview
@Composable
private fun ModuleCardPreview() {
    ModuleCard(
        module = WebUIViewModel.Module(
            name = "Example Module",
            id = "example_module",
            desc = "This is an example module used for previewing the ModuleCard composable in Jetpack Compose. It demonstrates how the module information will be displayed in the UI.",
            author = "John Doe",
            version = "1.0.0"
        )
    )
}
