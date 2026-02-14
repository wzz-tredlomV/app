package com.example.unarchiver

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun ui_elements_displayed() {
        onView(withId(R.id.btn_pick_files)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_pick_dest)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_compress)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_extract)).check(matches(isDisplayed()))
    }
}
