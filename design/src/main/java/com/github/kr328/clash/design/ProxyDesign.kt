package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    groupNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context), Parcelable {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private val config = ProxyViewConfig(context, uiStore.proxySingleLine)

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.singleLine = uiStore.proxySingleLine
        }
    }

    private val adapter: ProxyPageAdapter
        get() = binding.pagesView.adapter!! as ProxyPageAdapter

    private var horizontalScrolling = false
    private val verticalBottomScrolled: Boolean
        get() = adapter.states[binding.pagesView.currentItem].bottom
    private var urlTesting: Boolean
        get() = adapter.states[binding.pagesView.currentItem].urlTesting
        set(value) {
            adapter.states[binding.pagesView.currentItem].urlTesting = value
        }

    override val root: View = binding.root

    constructor(parcel: Parcel) : this(
        TODO("context"),
        TODO("overrideMode"),
        TODO("groupNames"),
        TODO("uiStore")
    ) {
        horizontalScrolling = parcel.readByte() != 0.toByte()
    }

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        adapter.updateAdapter(position, proxies, selectable, parent, links)

        adapter.states[position].urlTesting = false

        updateUrlTestButtonStatus()
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter.requestRedrawVisible()
        }
    }

    suspend fun requestDonate() {
        withContext(Dispatchers.Main) {
            val title = context.getText(R.string.request_donate)
            val message = context.getText(R.string.request_donate_tips)

            if (title.isNotEmpty() && message.isNotEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.request_donate)
                    .setMessage(R.string.request_donate_tips)
                    .setPositiveButton(R.string.ok) { _, _ -> }
                    .setCancelable(true)
                    .show()
            }
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.urlTestView.visibility = View.GONE
            binding.tabLayoutView.visibility = View.GONE
            binding.elevationView.visibility = View.GONE
            binding.pagesView.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(R.attr.colorOnPrimary)
            )

            binding.pagesView.apply {
                adapter = ProxyPageAdapter(
                    surface,
                    config,
                    List(groupNames.size) { index ->
                        ProxyAdapter(config) { name ->
                            requests.trySend(Request.Select(index, name))
                        }
                    }
                ) {
                    if (it == currentItem)
                        updateUrlTestButtonStatus()
                }

                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrollStateChanged(state: Int) {
                        horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE

                        updateUrlTestButtonStatus()
                    }

                    override fun onPageSelected(position: Int) {
                        uiStore.proxyLastGroup = groupNames[position]
                    }
                })
            }

            TabLayoutMediator(binding.tabLayoutView, binding.pagesView) { tab, index ->
                tab.text = groupNames[index]
            }.attach()

            val initialPosition = groupNames.indexOf(uiStore.proxyLastGroup)

            binding.pagesView.post {
                if (initialPosition > 0)
                    binding.pagesView.setCurrentItem(initialPosition, false)
            }
        }
    }

    fun requestUrlTesting() {
        urlTesting = true

        requests.trySend(Request.UrlTest(binding.pagesView.currentItem))

        updateUrlTestButtonStatus()
    }

    private fun updateUrlTestButtonStatus() {
        if (verticalBottomScrolled || horizontalScrolling || urlTesting) {
            binding.urlTestFloatView.hide()
        } else {
            binding.urlTestFloatView.show()
        }

        if (urlTesting) {
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (horizontalScrolling) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyDesign> {
        override fun createFromParcel(parcel: Parcel): ProxyDesign {
            return ProxyDesign(parcel)
        }

        override fun newArray(size: Int): Array<ProxyDesign?> {
            return arrayOfNulls(size)
        }
    }
}