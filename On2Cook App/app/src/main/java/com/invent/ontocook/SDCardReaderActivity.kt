package com.invent.ontocook

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.invent.ontocook.adapter.FilesListAdapter
import kotlinx.android.synthetic.main.activity_s_d_card_reader.*
import kotlinx.android.synthetic.main.view_header.*

class SDCardReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_s_d_card_reader)

        init()
    }

    fun init(){
        ivLeft.setOnClickListener {
            onBackPressed()
        }

        var layoutManager = LinearLayoutManager(this@SDCardReaderActivity,
            LinearLayoutManager.VERTICAL, false)
        rvFileListing.layoutManager = layoutManager
        var filesListAdapter  = FilesListAdapter(this@SDCardReaderActivity, object : FilesListAdapter.OnFileDeleteActionListener{
            override fun OnItemDelete(index: Int) {
                println("index of delete item   $index")
            }
        })
        rvFileListing.adapter = filesListAdapter
        filesListAdapter.setFilesList(mutableListOf("idli_recipe.txt", "veg_fried_rice.txt"))
    }
}