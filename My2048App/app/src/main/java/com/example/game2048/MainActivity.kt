
package com.example.game2048



import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.viewModels

import com.example.game2048.ui.GameScreen

import com.example.game2048.ui.My2048Theme



class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            My2048Theme {

                GameScreen(viewModel = vm)

            }

        }

    }

}

