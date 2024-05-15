package uk.arias.app_psi.activities

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import uk.arias.app_psi.R

class KeysActivity : AppCompatActivity() {


    private lateinit var textViewKeys: TextView
    private lateinit var textViewKeyValue: TextView
    private lateinit var buttonGoBack: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keys)
        // Get the contents from the extras
        val extras = intent.extras
        val publicKeys = extras?.getString("publicKey")
        val dataset = extras?.getString("dataset")
        val results = extras?.getString("results")

        textViewKeys = findViewById(R.id.textViewKeys)
        textViewKeyValue = findViewById(R.id.textViewKeyValue)
        buttonGoBack = findViewById(R.id.buttonGoBack)

        if (dataset != null) {
            textViewKeys.text = getString(R.string.dataset)
            textViewKeyValue.text = getString(R.string.dataset_res, dataset)
            textViewKeyValue.movementMethod = ScrollingMovementMethod()
        } else if (results != null) {
            textViewKeys.text = getString(R.string.results_ac)
            textViewKeyValue.text = getString(R.string.results_ac_res, results)
            textViewKeyValue.movementMethod = ScrollingMovementMethod()
        } else {
            textViewKeys.text = getString(R.string.keys_ac)
            textViewKeyValue.text = getString(R.string.public_keys, publicKeys)
            textViewKeyValue.movementMethod = ScrollingMovementMethod()
        }

        buttonGoBack.setOnClickListener {
            finish()
        }
    }
}