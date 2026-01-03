
package com.example.game2048



import androidx.compose.runtime.mutableStateOf

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.launch



class GameViewModel : ViewModel() {

    private val game = Game(4)



    val boardState = mutableStateOf(game.board.map { it.copyOf() })

    val score = mutableStateOf(game.score)

    val gameOver = mutableStateOf(false)



    init {

        refresh()

    }



    private fun refresh() {

        boardState.value = game.board.map { it.copyOf() }

        score.value = game.score

        gameOver.value = !game.canMove()

    }



    fun move(dir: Dir) {

        viewModelScope.launch {

            if (game.move(dir)) refresh()

        }

    }



    fun newGame() {

        game.reset()

        refresh()

    }

}

