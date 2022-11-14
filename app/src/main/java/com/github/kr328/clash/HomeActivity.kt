package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.*
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.store.TipsStore
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
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
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        HomeDesign.Request.ShowNodeList -> {
                            // 获取节点数据

                            Log.e("点击Best Nodes")
                            startActivity(NodeListActivity::class.intent)
                        }
                        HomeDesign.Request.FetchNodeList -> {
                            // 增加Profile配置， url: http://sub.free88.top/config.yaml
                            // 显示节点列表



1
                        }
                        HomeDesign.Request.Connect -> {
                            // 开始连接
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                            // 启动clash
                            Log.e("Start Clash")

                        }
                        HomeDesign.Request.ShowSetting -> {
                            Log.e("进入clash主界面")
                            startActivity(MainActivity::class.intent)
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
            showToast(com.github.kr328.clash.design.R.string.no_profile_selected, ToastDuration.Long) {
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
            design?.showToast(com.github.kr328.clash.design.R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }
}