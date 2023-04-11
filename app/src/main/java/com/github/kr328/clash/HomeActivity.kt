package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.HomeDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.store.TipsStore
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import java.util.UUID
import java.util.concurrent.TimeUnit

class HomeActivity : BaseActivity<HomeDesign>() {
    override suspend fun main() {
        val design = HomeDesign(this)

        setContentDesign(design)

        launch(Dispatchers.IO) {
            showUpdatedTips(design)
        }

        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.fetch()
                            design.addDefaultProfile()
                            design.verifyAndCommit()
                        }
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded,
                        Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is HomeDesign.Request.FetchNodeList -> {
                            //design.verifyAndCommit()
                            Log.e("点击Best Nodes")
                            if (clashRunning)
                                startActivity(NodeListActivity::class.intent)
                            else {
                                Log.e("Need start FreeGate first")
                            }
                        }
                        is HomeDesign.Request.Connect -> {
                            // 开始连接
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                            // 启动clash
                            Log.e("Start Clash")

                        }
                        is HomeDesign.Request.ShowSetting -> {
                            Log.e("进入clash主界面")
                            startActivity(MainActivity::class.intent)
                        }
                        is HomeDesign.Request.AddDefault -> {
                            Log.e("开始添加默认配置")
                            design.addDefaultProfile()
                            Log.e("开始导入默认配置")
                            design.verifyAndCommit()
                            Log.e("开始激活默认配置")
                            withProfile { setActive(design.profile) }

                        }
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun showUpdatedTips(design: HomeDesign) {
        val tips = TipsStore(this)

        if (tips.primaryVersion != TipsStore.CURRENT_PRIMARY_VERSION) {
            tips.primaryVersion = TipsStore.CURRENT_PRIMARY_VERSION

            val pkg = packageManager.getPackageInfo(packageName, 0)

            if (pkg.firstInstallTime != pkg.lastUpdateTime) {
                design.showUpdatedTips()
            }
        }
    }

    private suspend fun HomeDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun HomeDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private suspend fun HomeDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(
                com.github.kr328.clash.design.R.string.no_profile_selected,
                ToastDuration.Long
            ) {
                setAction(com.github.kr328.clash.design.R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == AppCompatActivity.RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(
                com.github.kr328.clash.design.R.string.unable_to_start_vpn,
                ToastDuration.Long
            )
        }
    }

    private suspend fun HomeDesign.addDefaultProfile() {
        return withProfile {
            var uuid: UUID
            val original: Profile?
            var url = "https://sub.free88.top/dWaExU_clash"
            var name = "test999"

            var profiles = queryAll()

            if (profiles.isEmpty()) {
                uuid = create(Profile.Type.Url, name, url)
                original = queryByUUID(uuid)
                var profileTmp = original?.copy(interval = 86400000)
                if (profileTmp != null) {
                    //setActive(profileTmp)
                    design!!.profile = profileTmp
                }

                Log.e("Add default profile: $url, $uuid")
            }
            else {
                var activeProfile = queryActive()
                if (activeProfile == null) {
                    setActive(profiles[0])
                    design!!.profile = profiles[0]
                }
                else {
                    design!!.profile = activeProfile
                }
                Log.e("Current use profile: ${activeProfile?.name}, ${activeProfile?.uuid}")
            }
        }
    }

    private suspend fun HomeDesign.verifyAndCommit() {
        if (clashRunning) return
        var activityProfile = withProfile { queryActive() }
        if (activityProfile != null) return

        when {
            profile.name.isBlank() -> {
                showToast(R.string.empty_name, ToastDuration.Long)
            }
            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                showToast(R.string.invalid_url, ToastDuration.Long)
            }
            else -> {
                try {
                    withProcessing { updateStatus ->
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, 86400000)
                            Log.e("更新间隔，${profile.uuid}")

                            coroutineScope {
                                commit(profile.uuid) {
                                    launch {
                                        updateStatus(it)
                                    }
                                }
                            }
                        }
                    }

                    //setResult(AppCompatActivity.RESULT_OK)

                    //finish()
                    withProfile {
                        setActive(profile)
                        Log.e("Current use profile: ${profile?.name}, ${profile?.uuid}")
                    }

                } catch (e: Exception) {
                    showExceptionToast(e)
                }
            }
        }
    }

}