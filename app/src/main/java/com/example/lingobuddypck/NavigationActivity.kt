package com.example.lingobuddypck

import android.os.Bundle
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.lingobuddypck.databinding.ActivityNavigationBinding

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding

    private lateinit var bottomNav: BottomNavigationView

    fun hideBottomNavigationBar() {
        bottomNav.visibility = View.GONE // Hoặc View.INVISIBLE nếu bạn muốn giữ không gian
    }

    fun showBottomNavigationBar() {
        bottomNav.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navView
        bottomNav = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_activity_navigation)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.hide()
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_saved_words, R.id.navigation_notifications
            )
        )
        bottomNav.itemIconTintList = null
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}