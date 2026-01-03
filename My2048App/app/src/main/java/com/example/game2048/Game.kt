
package com.example.game2048



import kotlin.random.Random



enum class Dir { UP, DOWN, LEFT, RIGHT }



class Game(private val size: Int = 4) {

    var board: Array<IntArray> = Array(size) { IntArray(size) }

        private set

    var score: Int = 0

        private set



    init {

        reset()

    }



    fun reset() {

        board = Array(size) { IntArray(size) }

        score = 0

        addRandomTile()

        addRandomTile()

    }



    fun move(dir: Dir): Boolean {

        when (dir) {

            Dir.UP -> rotateLeft()

            Dir.RIGHT -> rotate180()

            Dir.DOWN -> rotateRight()

            else -> {}

        }

        val moved = moveLeft()

        when (dir) {

            Dir.UP -> rotateRight()

            Dir.RIGHT -> rotate180()

            Dir.DOWN -> rotateLeft()

            else -> {}

        }

        if (moved) addRandomTile()

        return moved

    }



    private fun moveLeft(): Boolean {

        var moved = false

        for (i in 0 until size) {

            val row = board[i]

            val compressed = IntArray(size)

            var idx = 0

            for (j in 0 until size) {

                if (row[j] != 0) compressed[idx++] = row[j]

            }

            var j = 0

            while (j < size - 1) {

                if (compressed[j] != 0 && compressed[j] == compressed[j + 1]) {

                    compressed[j] = compressed[j] * 2

                    score += compressed[j]

                    compressed[j + 1] = 0

                    j += 2

                } else j++

            }

            val newRow = IntArray(size)

            idx = 0

            for (k in 0 until size) if (compressed[k] != 0) newRow[idx++] = compressed[k]

            for (k in 0 until size) {

                if (board[i][k] != newRow[k]) {

                    moved = true

                    board[i][k] = newRow[k]

                }

            }

        }

        return moved

    }



    private fun rotateLeft() {

        val t = Array(size) { IntArray(size) }

        for (i in 0 until size) for (j in 0 until size) t[size - 1 - j][i] = board[i][j]

        board = t

    }



    private fun rotateRight() {

        val t = Array(size) { IntArray(size) }

        for (i in 0 until size) for (j in 0 until size) t[j][size - 1 - i] = board[i][j]

        board = t

    }



    private fun rotate180() {

        val t = Array(size) { IntArray(size) }

        for (i in 0 until size) for (j in 0 until size) t[size - 1 - i][size - 1 - j] = board[i][j]

        board = t

    }



    fun addRandomTile() {

        val empties = mutableListOf<Pair<Int, Int>>()

        for (i in 0 until size) for (j in 0 until size) if (board[i][j] == 0) empties.add(i to j)

        if (empties.isEmpty()) return

        val (r, c) = empties.random(Random)

        board[r][c] = if (Random.nextDouble() < 0.9) 2 else 4

    }



    fun canMove(): Boolean {

        for (i in 0 until size) for (j in 0 until size) {

            if (board[i][j] == 0) return true

            if (j + 1 < size && board[i][j] == board[i][j + 1]) return true

            if (i + 1 < size && board[i][j] == board[i + 1][j]) return true

        }

        return false

    }

}

