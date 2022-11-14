package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.databinding.DesignHomeBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeDesign(context: Context) : Design<HomeDesign.Request>(context) {
    sealed class Request {
        object FetchNodeList : Request()
        object ShowNodeList : Request()
        object ShowSetting : Request()
        object Connect : Request()

        data class Active(val profile: Profile) : ProfilesDesign.Request()
    }

    private val binding = DesignHomeBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    /*var profile: Profile
        get() = binding.profile!!
        set(value) {
            binding.profile = value
        }*/

    /*val progressing: Boolean
        get() = binding.hasPendingBindings()

    private val adapter = ProfileAdapter(context, this::requestActive, this::showMenu)

    suspend fun patchProfiles(profiles: List<Profile>) {
        adapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            //binding.updateView.visibility = if (updatable) View.VISIBLE else View.GONE
        }
    }

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            //binding.processing = true

            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = context.getString(R.string.initializing)
                }

                executeTask {
                    configure {
                        //applyFrom(it)
                    }
                }
            }
        } finally {
            //binding.processing = false
        }
    }

    private fun requestActive(profile: Profile) {
        //requests.trySend(Request.Active(profile))
    }

    private fun showMenu(profile: Profile) {

    }

    suspend fun showNodeList(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignNodeListBinding.inflate(context.layoutInflater).apply {
                //this.versionName = versionName
            }

            var dialog = AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
            dialog.window?.setLayout(1024, 1600)
        }
    }*/

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                TunnelState.Mode.Script -> context.getString(R.string.script_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showUpdatedTips() {
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.version_updated)
                .setMessage(R.string.version_updated_tips)
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        }
    }

    init {
        binding.self = this

        //binding.activityBarLayout.applyFrom(context)

        binding.colorClashStarted = context.resolveThemedColor(R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

    }
}