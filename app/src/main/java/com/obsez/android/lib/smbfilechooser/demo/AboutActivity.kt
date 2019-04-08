package com.obsez.android.lib.smbfilechooser.demo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.obsez.android.lib.smbfilechooser.internals.UiUtil

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        (findViewById<FloatingActionButton>(R.id.fab)).setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupUi()
    }

    private fun setupUi() {
        setupRecyclerView(findViewById<RecyclerView>(R.id.recyclerView1))
    }

    private fun setupRecyclerView(rv: RecyclerView) {
        rv.apply {
            val linearLayoutManager = object : LinearLayoutManager(this.context) {
                override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
                    return UiUtil.dip2px(56)
                }
            }
            this.layoutManager = linearLayoutManager

            this.addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.HORIZONTAL))

            this.itemAnimator = DefaultItemAnimator()

            this.adapter = MainAdapter(this@AboutActivity, aboutItems)
        }
    }

    class MainAdapter(private val ctx: AppCompatActivity, items: List<Items>) : RecyclerView.Adapter<MainAdapter.ViewHolder>() {

        var plainItems: MutableList<Item> = mutableListOf()
        init {
            for (it in items) {
                if (it.items.isNotEmpty())
                    it.items[0].catalog = it.title
                plainItems.addAll(it.items)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            //return ViewHolder(TextView(parent.context))
            return ViewHolder(LayoutInflater.from(ctx).inflate(R.layout.li_about_item, parent, false)) { _, holder ->
                if (holder.mValueView.tag != null && holder.mValueView.tag is String) {
                    val link: String = holder.mValueView.tag as String
                    when {
                        link.startsWith("mailto:") -> ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(link)))
                        link.startsWith("tel:") -> ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(link)))
                        link.startsWith("market:") -> {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse(link))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            try {
                                ctx.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("http://play.google.com/store/apps/details?id=" + ctx.getPackageName())))
                            }
                        }
                        else -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return plainItems.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val it = plainItems[position]
            holder.mTitleView.text = it.title
            holder.mSubTitleView.text = it.subTitle
            holder.mValueView.text = it.value

            if (it.subTitle.isBlank()) {
                holder.mSubTitleView.visibility = View.GONE
            } else {
                holder.mSubTitleView.visibility = View.VISIBLE
            }
            if (!it.catalog.isNullOrBlank()) {
                holder.mCatalogView.text = it.catalog
                holder.mCatalogView.visibility = View.VISIBLE
            } else {
                holder.mCatalogView.visibility = View.GONE
            }

            //holder.mValueView.isClickable = !it.valueLink.isBlank()
            holder.mValueView.tag = it.valueLink
            //holder.mIconView.text = it.title
        }

        class ViewHolder(view: View, clicking: ((v: View, holder: MainAdapter.ViewHolder) -> Unit)? = null) : RecyclerView.ViewHolder(view) {
            internal var mTitleView = view.findViewById<TextView>(R.id.title)
            internal var mSubTitleView = view.findViewById<TextView>(R.id.sub_title)
            internal var mValueView = view.findViewById<TextView>(R.id.value)
            internal var mCatalogView = view.findViewById<TextView>(R.id.catalog)

            init {
                view.findViewById<View>(R.id.row)?.setOnClickListener {
                    clicking?.invoke(it, this)
                }
            }
        }
    }

    companion object {
        val aboutItems = listOf(
            Items("Information", listOf(
                Item("Homepage", "Goto", "https://github.com/hedzr/android-file-chooser"),
                Item("Issues", "Report to us", "https://github.com/hedzr/android-file-chooser/issues/new"),
                Item("License", "Apache 2.0", "https://github.com/hedzr/android-file-chooser/blob/master/LICENSE"),
                Item("Rate me", "Like!", "market://details?id=" + "com.obsez.android.lib.filechooser")

            )),
            Items("Credits", listOf(
                Item("Hedzr Yeh", "Email", "mailto:hedzrz@gmail.com", "Maintainer"),
                Item("Guiorgy Potskhishvili", "Email", "mailto:guiorgy123@gmail.com", "Maintainer"),
                Item("iqbalhood", "Email", "iqbalhood@gmail.com", "Logo and banner maker"),
                Item("More Contributors", "Goto", "https://github.com/hedzr/android-file-chooser#Acknowledges", "and supporters")
            ))
        )
    }
}

class Items(var title: String, var items: List<Item>)
class Item(var title: String, var value: String, var valueLink: String = "", var subTitle: String = "", var catalog: String? = null)
