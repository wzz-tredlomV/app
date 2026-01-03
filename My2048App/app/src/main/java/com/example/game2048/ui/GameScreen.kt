
package com.example.game2048.ui



import androidx.compose.animation.animateContentSize

import androidx.compose.foundation.background

import androidx.compose.foundation.gestures.detectDragGestures

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.unit.dp

import com.example.game2048.Dir

import com.example.game2048.GameViewModel

import kotlin.math.abs



@Composable

fun GameScreen(viewModel: GameViewModel) {

    val board by viewModel.boardState

    val score by viewModel.score

    val gameOver by viewModel.gameOver



    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

        Column(modifier = Modifier

            .fillMaxSize()

            .padding(16.dp)) {

            TopBar(score = score, onNewGame = { viewModel.newGame() })

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier

                .weight(1f)

                .fillMaxWidth(),

                contentAlignment = Alignment.Center

            ) {

                GameBoard(board = board, onSwipe = { d -> viewModel.move(d) })

                if (gameOver) {

                    Card(

                        shape = RoundedCornerShape(12.dp),

                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),

                        modifier = Modifier

                            .padding(16.dp)

                            .fillMaxWidth(0.7f)

                    ) {

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {

                            Text("Game Over", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)

                            Spacer(Modifier.height(8.dp))

                            TextButton(onClick = { viewModel.newGame() }) {

                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)

                                Spacer(Modifier.width(8.dp))

                                Text("Restart")

                            }

                        }

                    }

                }

            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Swipe 任意方向合并方块，目标 2048", style = MaterialTheme.typography.bodySmall)

        }

    }

}



@Composable

private fun TopBar(score: Int, onNewGame: () -> Unit) {

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

        Column {

            Text("2048", style = MaterialTheme.typography.headlineMedium)

            Text("Score: $score", style = MaterialTheme.typography.bodyMedium)

        }

        IconButton(onClick = onNewGame) {

            Icon(imageVector = Icons.Default.Refresh, contentDescription = "New Game")

        }

    }

}



@Composable

private fun GameBoard(board: List<IntArray>, onSwipe: (Dir) -> Unit) {

    val size = board.size

    val gap = 8.dp



    Column(

        modifier = Modifier

            .aspectRatio(1f)

            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))

            .padding(gap)

            .pointerInput(Unit) {

                detectDragGestures { change, dragAmount ->

                    // handled by onDragEnd below; use offset accumulation

                }

            }

            .pointerInput(Unit) {

                awaitPointerEventScope {

                    while (true) {

                        val down = awaitPointerEventScope { awaitFirstDown().position }

                        var total = Offset.Zero

                        var finished = false

                        while (!finished) {

                            val event = awaitPointerEvent()

                            val move = event.changes.firstOrNull()

                            if (move != null && move.pressed) {

                                total += move.positionChange()

                                move.consume()

                            } else {

                                finished = true

                                val dx = total.x

                                val dy = total.y

                                if (abs(dx) > abs(dy)) {

                                    if (dx > 40) onSwipe(Dir.RIGHT) else if (dx < -40) onSwipe(Dir.LEFT)

                                } else {

                                    if (dy > 40) onSwipe(Dir.DOWN) else if (dy < -40) onSwipe(Dir.UP)

                                }

                            }

                        }

                    }

                }

            }

    ) {

        for (i in 0 until size) {

            Row(modifier = Modifier.weight(1f)) {

                for (j in 0 until size) {

                    Box(modifier = Modifier

                        .weight(1f)

                        .padding(gap)

                        .animateContentSize()

                    ) {

                        Tile(value = board[i][j])

                    }

                }

            }

        }

    }

}

