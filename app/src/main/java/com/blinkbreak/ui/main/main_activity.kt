package com.blinkbreak.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blinkbreak.databinding.ActivityMainBinding
import com.blinkbreak.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.blinkbreak.R.id.fragment_container, HomeFragment())
                .commit()
        }
    }
}
