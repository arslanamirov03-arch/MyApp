package com.arslan.b1vokabeln

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.arslan.b1vokabeln.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: WordAdapter
    private lateinit var prefs: SharedPreferences

    private val allWords = ArrayList<Word>()
    private val learned = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_LEARNED, emptySet())?.let { learned.addAll(it) }

        loadWords()

        adapter = WordAdapter { word, isChecked ->
            word.learned = isChecked
            if (isChecked) learned.add(word.id.toString()) else learned.remove(word.id.toString())
            prefs.edit().putStringSet(KEY_LEARNED, HashSet(learned)).apply()
            updateProgress()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        adapter.submit(allWords)

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        updateProgress()
    }

    private fun loadWords() {
        assets.open("words.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            var index = 0
            lines.forEach { raw ->
                val text = raw.trim()
                if (text.isNotEmpty()) {
                    index++
                    allWords.add(Word(index, text, learned.contains(index.toString())))
                }
            }
        }
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allWords else allWords.filter { it.text.lowercase().contains(q) }
        adapter.submit(filtered)
    }

    private fun updateProgress() {
        val total = allWords.size
        val done = allWords.count { it.learned }
        val percent = if (total == 0) 0 else done * 100 / total
        binding.progressText.text = getString(R.string.progress_format, done, total, percent)
        binding.progressBar.max = if (total == 0) 1 else total
        binding.progressBar.setProgressCompat(done, true)
    }

    companion object {
        private const val PREFS = "progress"
        private const val KEY_LEARNED = "learned"
    }
}
