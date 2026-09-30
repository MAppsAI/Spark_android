package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.NodeDetailScreen
import com.example.ui.screens.NodesListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TailNodeViewModel
import com.example.viewmodel.TailNodeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                TailNodeApp()
            }
        }
    }
}

@Composable
fun TailNodeApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as android.app.Application
    val viewModel: TailNodeViewModel = viewModel(
        factory = TailNodeViewModelFactory(application)
    )
    val navController = rememberNavController()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = "nodes",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("nodes") {
            NodesListScreen(
                viewModel = viewModel,
                onNavigateToNode = { node, featureIndex ->
                    viewModel.selectNode(node)
                    viewModel.setSelectedTab(featureIndex)
                    navController.navigate("node_detail")
                }
            )
        }

        composable("node_detail") {
            selectedNode?.let { node ->
                NodeDetailScreen(
                    node = node,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

