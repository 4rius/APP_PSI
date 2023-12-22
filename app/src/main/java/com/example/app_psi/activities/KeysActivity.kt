package com.example.app_psi.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.app_psi.R

class KeysActivity : AppCompatActivity() {


    private lateinit var textViewKeys: TextView
    private lateinit var textViewKeyValue: TextView
    private lateinit var buttonGoBack: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keys)
        // Get the contents from the extras
        val extras = intent.extras
        val publicKey = extras?.getString("publicKey")
        val privateKey = extras?.getString("privateKey")
        val dataset = extras?.getString("dataset")
        val results = extras?.getString("results")

        textViewKeys = findViewById(R.id.textViewKeys)
        textViewKeyValue = findViewById(R.id.textViewKeyValue)
        buttonGoBack = findViewById(R.id.buttonGoBack)

        if (dataset != null) {
            textViewKeys.text = "Dataset"
            textViewKeyValue.text = "Dataset: $dataset"
        } else if (results != null) {
            textViewKeys.text = "Results"
            textViewKeyValue.text = "Results: $results"
        } else {
            textViewKeys.text = "Keys"
            textViewKeyValue.text = "Public key (n): $publicKey\nPrivate key (lambda): $privateKey"
        }

        buttonGoBack.setOnClickListener {
            finish()
        }
    }
}